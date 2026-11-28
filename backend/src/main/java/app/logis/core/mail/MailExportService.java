package app.logis.core.mail;

import app.logis.shared.GermanTime;
import app.logis.core.ledger.LegalEntities;
import app.logis.core.realestate.PropertyAddresses;
import app.logis.core.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Admin export of pseudonymized mails from consenting mailboxes (design.md of admin-mail-export, D2–D7). The ZIP is
 * written straight to the response and never stored.
 */
@Service
class MailExportService {

    static final int MAX_MAILS = 2000;
    static final String TOO_MANY = "Zu viele Mails für einen Export. Bitte den Zeitraum oder die Filter einschränken.";
    static final String NOT_FOUND = "Postfach nicht gefunden";

    private static final Logger log = LoggerFactory.getLogger(MailExportService.class);
    private static final ZoneId ZONE = GermanTime.ZONE;
    private static final Map<String, String> CORE_LABELS = Map.of(StoredMail.NOT_RECOGNISED, "Nicht erkannt",
            StoredMail.IGNORED, "Ignoriert", StoredMail.FAILED, "Fehler");

    private final JdbcTemplate jdbc;
    private final List<MailHandler> handlers;
    private final MailExportLog exportLog;
    private final ObjectMapper json;
    private final Clock clock;
    private final PropertyAddresses properties;
    private final LegalEntities legalEntities;

    MailExportService(JdbcTemplate jdbc, List<MailHandler> handlers, MailExportLog exportLog, ObjectMapper json,
            Clock clock, PropertyAddresses properties, LegalEntities legalEntities) {
        this.properties = properties;
        this.legalEntities = legalEntities;
        this.jdbc = jdbc;
        this.handlers = handlers;
        this.exportLog = exportLog;
        this.json = json;
        this.clock = clock;
    }

    record ExportMailbox(Long ownerUserId, String username, String displayName) {
    }

    /** {@code results} and {@code sources} empty: all; {@code from}/{@code to}: received days, inclusive. */
    record Filter(List<Long> owners, LocalDate from, LocalDate to, List<String> results, List<String> sources) {

        Filter {
            owners = owners == null ? List.of() : List.copyOf(owners);
            results = results == null ? List.of() : List.copyOf(results);
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    private record Row(Long id, Long ownerUserId, Instant receivedAt, String result, String kind, String source) {
    }

    List<ExportMailbox> mailboxes() {
        return jdbc.query("SELECT m.owner_user_id, u.username, u.display_name FROM core.mailboxes m "
                        + "JOIN core.users u ON u.id = m.owner_user_id WHERE m.admin_export_allowed "
                        + "ORDER BY lower(u.display_name)",
                (rs, i) -> new ExportMailbox(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    List<String> agencies(List<Long> owners) {
        var allowed = allowed(owners);
        if (allowed.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("SELECT DISTINCT source FROM core.mails WHERE source IS NOT NULL AND result <> 'DELETED' "
                + "AND owner_user_id = ANY(?) ORDER BY source", String.class, (Object) allowed.toArray(Long[]::new));
    }

    /** Checks consent and the size before anything is written; returns the number of mails. */
    int check(Filter filter) {
        requireConsent(filter.owners());
        var count = rows(filter).size();
        if (count > MAX_MAILS) {
            throw ApiException.badRequest(TOO_MANY);
        }
        return count;
    }

    /** Writes the ZIP and records the export per mailbox once it is complete. */
    void export(Long adminUserId, Filter filter, OutputStream out) throws IOException {
        requireConsent(filter.owners());
        var rows = rows(filter);
        var pseudonymizer = new Pseudonymizer(new SecureRandom());
        var rewriter = new MimeRewriter(pseudonymizer);
        var sources = new HashMap<Long, List<MailHandler.SensitiveValues>>();
        var ownerValues = new HashMap<Long, Set<SensitiveValue>>();
        // Addresses on agency domains stay: domains of senders whose mails a handler took (design D5).
        var keepDomains = MimeRewriter.agencyDomains(jdbc.queryForList("SELECT DISTINCT sender FROM core.mails "
                + "WHERE handler IS NOT NULL AND result <> 'DELETED' AND owner_user_id = ANY(?)", String.class,
                (Object) filter.owners().toArray(Long[]::new)));
        handlers.forEach(h -> keepDomains.addAll(MimeRewriter.agencyDomains(h.agencyDomains().stream()
                .map(d -> "@" + d).toList())));
        var resultLabels = new HashMap<>(CORE_LABELS);
        var kindLabels = new HashMap<String, String>();
        handlers.forEach(h -> {
            resultLabels.putAll(h.resultLabels());
            kindLabels.putAll(h.kindLabels());
        });
        var usernames = usernames(filter.owners());
        var index = new StringBuilder("﻿Datei;Eingang;Absender;Betreff;Ergebnis;Art;Vermittler;Postfach\r\n");
        var counts = new LinkedHashMap<Long, Integer>();
        filter.owners().forEach(o -> counts.put(o, 0));
        // Not closed here: the response must stay open until the log is written, so that a client that has the
        // complete download also finds the export in the log.
        var zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        {
            int n = 0;
            for (var row : rows) {
                n++;
                var raw = jdbc.queryForObject("SELECT raw FROM core.mails WHERE id = ?", byte[].class, row.id());
                var name = "%04d-%s%s.eml".formatted(n, slug(row.result()), row.source() == null ? "" : "-" + slug(row.source()));
                String sender = null;
                String subject = null;
                try {
                    var perOwner = sources.computeIfAbsent(row.ownerUserId(),
                            owner -> handlers.stream().map(h -> h.sensitiveValues(owner)).toList());
                    var content = MailContent.parse(raw);
                    var values = new HashSet<>(ownerValues.computeIfAbsent(row.ownerUserId(), this::ownerValues));
                    perOwner.forEach(s -> values.addAll(s.of(content)));
                    var rewritten = rewriter.rewrite(raw, values, keepDomains);
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(rewritten.eml());
                    zip.closeEntry();
                    sender = rewritten.sender();
                    subject = rewritten.subject();
                } catch (Exception ex) {
                    // Never fall back to the original: the mail is listed but left out.
                    log.warn("Mail {} could not be pseudonymized: {}", row.id(), ex.toString());
                    name = "nicht exportierbar";
                }
                counts.merge(row.ownerUserId(), 1, Integer::sum);
                index.append(csv(name)).append(';')
                        .append(csv(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(row.receivedAt().atZone(ZONE)
                                .toLocalDateTime().withNano(0)))).append(';')
                        .append(csv(sender)).append(';').append(csv(subject)).append(';')
                        .append(csv(resultLabels.getOrDefault(row.result(), row.result()))).append(';')
                        .append(csv(row.kind() == null ? null : kindLabels.getOrDefault(row.kind(), row.kind())))
                        .append(';').append(csv(row.source())).append(';')
                        .append(csv(usernames.get(row.ownerUserId()))).append("\r\n");
            }
            zip.putNextEntry(new ZipEntry("index.csv"));
            zip.write(index.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
        }
        var now = clock.instant();
        var filters = filtersJson(filter);
        counts.forEach((owner, count) -> exportLog.record(adminUserId, owner, now, count, filters));
        zip.flush();
    }

    /**
     * The owner's own values (design D1 of mail-export-pseudonymization): display name and username, the legal
     * entities they manage or whose properties they see, and the addresses of the properties they see.
     */
    private Set<SensitiveValue> ownerValues(Long owner) {
        var values = new HashSet<SensitiveValue>();
        jdbc.query("SELECT display_name, username FROM core.users WHERE id = ?", rs -> {
            SensitiveValue.of(SensitiveValue.Kind.OWNER, rs.getString(1)).ifPresent(values::add);
            SensitiveValue.of(SensitiveValue.Kind.OWNER, rs.getString(2)).ifPresent(values::add);
        }, owner);
        var visible = properties.visibleTo(owner);
        var entityIds = new HashSet<Long>();
        for (var p : visible) {
            entityIds.add(p.legalEntityId());
            SensitiveValue.of(SensitiveValue.Kind.STREET, p.street()).ifPresent(values::add);
            SensitiveValue.of(SensitiveValue.Kind.POSTAL_CODE, p.postalCode()).ifPresent(values::add);
            SensitiveValue.of(SensitiveValue.Kind.CITY, p.city()).ifPresent(values::add);
        }
        legalEntities.managed(owner).forEach(e -> entityIds.add(e.id()));
        legalEntities.summaries(entityIds).values()
                .forEach(e -> SensitiveValue.of(SensitiveValue.Kind.LEGAL_ENTITY, e.name()).ifPresent(values::add));
        return values;
    }

    List<MailExportLog.Entry> log() {
        return exportLog.all();
    }

    /** Consenting owners among {@code owners}. */
    private List<Long> allowed(List<Long> owners) {
        if (owners.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("SELECT owner_user_id FROM core.mailboxes WHERE admin_export_allowed "
                + "AND owner_user_id = ANY(?)", Long.class, (Object) owners.toArray(Long[]::new));
    }

    /** A mailbox without consent is treated as unknown, so its existence is not revealed (design D1). */
    private void requireConsent(List<Long> owners) {
        if (owners.isEmpty()) {
            throw ApiException.badRequest("Bitte mindestens ein Postfach wählen.");
        }
        if (new HashSet<>(allowed(owners)).size() != new HashSet<>(owners).size()) {
            throw ApiException.notFound(NOT_FOUND);
        }
    }

    private List<Row> rows(Filter filter) {
        var sql = new StringBuilder("SELECT id, owner_user_id, received_at, result, kind, source FROM core.mails "
                + "WHERE result <> 'DELETED' AND owner_user_id = ANY(?)");
        var args = new ArrayList<Object>();
        args.add(filter.owners().toArray(Long[]::new));
        if (filter.from() != null) {
            sql.append(" AND received_at >= ?");
            args.add(Timestamp.from(filter.from().atStartOfDay(ZONE).toInstant()));
        }
        if (filter.to() != null) {
            sql.append(" AND received_at < ?");
            args.add(Timestamp.from(filter.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        if (!filter.results().isEmpty()) {
            sql.append(" AND result = ANY(?)");
            args.add(filter.results().toArray(String[]::new));
        }
        if (!filter.sources().isEmpty()) {
            sql.append(" AND source = ANY(?)");
            args.add(filter.sources().toArray(String[]::new));
        }
        sql.append(" ORDER BY received_at, id");
        return jdbc.query(sql.toString(), (rs, i) -> new Row(rs.getLong("id"), rs.getLong("owner_user_id"),
                rs.getTimestamp("received_at").toInstant(), rs.getString("result"), rs.getString("kind"),
                rs.getString("source")), args.toArray());
    }

    private Map<Long, String> usernames(List<Long> owners) {
        var result = new HashMap<Long, String>();
        jdbc.query("SELECT id, username FROM core.users WHERE id = ANY(?)",
                rs -> {
                    result.put(rs.getLong(1), rs.getString(2));
                }, (Object) owners.toArray(Long[]::new));
        return result;
    }

    private String filtersJson(Filter filter) {
        try {
            return json.writeValueAsString(filter);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String slug(String value) {
        var ascii = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return ascii.isEmpty() ? "mail" : ascii;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        var clean = value.replace("\r", " ").replace("\n", " ");
        // Spreadsheet formula injection: a leading =, +, - or @ is neutralised.
        if (!clean.isEmpty() && "=+-@".indexOf(clean.charAt(0)) >= 0) {
            clean = "'" + clean;
        }
        return clean.contains(";") || clean.contains("\"") ? "\"" + clean.replace("\"", "\"\"") + "\"" : clean;
    }
}
