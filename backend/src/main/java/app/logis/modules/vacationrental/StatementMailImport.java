package app.logis.modules.vacationrental;

import app.logis.shared.GermanTime;
import app.logis.core.documents.Documents;
import app.logis.core.mail.Handled;
import app.logis.core.mail.MailAttachments;
import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Statement mails of agencies (spec booking-mails, „Statement mails“ and „Fewo & Meer statement PDF“; design of
 * statement-mails). Recognised by rules only and asked before any other recognition, so neither the mail nor its PDF
 * ever reaches the AI.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class StatementMailImport {

    static final String KIND = "STATEMENT";
    /** Property name of the mapping that gives the unit of statement notices naming no property (design D9). */
    static final String STATEMENTS_MAPPING = "Abrechnungen";
    static final String PDF_NOT_READ = "PDF nicht gelesen";
    static final String ALREADY_RECORDED = "Abrechnung bereits erfasst";
    static final String ALREADY_SETTLED = "bereits abgerechnet";
    static final String EXTENSION_NOTE = "Verlängerungsbuchung";

    private static final ZoneId ZONE = GermanTime.ZONE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Session SESSION = Session.getInstance(new Properties());
    private static final Pattern FEWO_SUBJECT = Pattern.compile("^Ihre Abrechnung für (\\p{L}+) (\\d{4}) - (.+)$");
    private static final Pattern NOTICE_SUBJECT = Pattern.compile("^Ihre Abrechnung: (\\S+)$");
    private static final Pattern NOTICE_PERIOD = Pattern.compile(
            "Zeitraum vom (\\d{2}\\.\\d{2}\\.\\d{4}) bis zum (\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern FORWARDED_FROM = Pattern.compile(
            "(?m)^(?:Von|From):\\s*\"?([^\"<\\n]+?)\"?\\s*<[^>\\n]+>");

    /** What the mail itself says; {@code propertyName} is empty for notices that name no property. */
    record Recognised(String agency, String propertyName, LocalDate periodFrom, LocalDate periodTo,
            String invoiceNumber, boolean fewo) {
    }

    /** Stored with the mail as JSON. */
    record Summary(int rows, int assigned, int open) {
    }

    private final AgencyService agencies;
    private final UnitMappingRepository mappings;
    private final RentalAccess access;
    private final StatementService statements;
    private final StatementRepository statementRepository;
    private final StatementLineRepository lines;
    private final BookingRepository bookings;
    private final Documents documents;
    private final Clock clock;

    StatementMailImport(AgencyService agencies, UnitMappingRepository mappings, RentalAccess access,
            StatementService statements, StatementRepository statementRepository, StatementLineRepository lines,
            BookingRepository bookings, Documents documents, Clock clock) {
        this.agencies = agencies;
        this.mappings = mappings;
        this.access = access;
        this.statements = statements;
        this.statementRepository = statementRepository;
        this.lines = lines;
        this.bookings = bookings;
        this.documents = documents;
        this.clock = clock;
    }

    // --- Recognition (design D2) ---------------------------------------------------------------

    static Optional<Recognised> recognise(MailContent mail, byte[] raw) {
        var subject = mail.businessSubject();
        var m = FEWO_SUBJECT.matcher(subject);
        if (m.matches() && FewoUndMeerParser.fromAgency(mail)) {
            var month = GermanText.month(m.group(1));
            if (month.isEmpty()) {
                return Optional.empty();
            }
            var period = YearMonth.of(Integer.parseInt(m.group(2)), month.getAsInt());
            return Optional.of(new Recognised(FewoUndMeerParser.AGENCY, m.group(3).strip(), period.atDay(1),
                    period.atEndOfMonth(), null, true));
        }
        m = NOTICE_SUBJECT.matcher(subject);
        if (m.matches() && mail.text().contains("Eigentümerabrechnung")) {
            var period = NOTICE_PERIOD.matcher(mail.text());
            if (!period.find()) {
                return Optional.empty();
            }
            var from = LocalDate.parse(period.group(1), DATE);
            var to = LocalDate.parse(period.group(2), DATE);
            if (to.isBefore(from)) {
                return Optional.empty();
            }
            return Optional.of(new Recognised(HoliduDsParser.agency(senderName(mail, raw)), null, from, to,
                    m.group(1), false));
        }
        return Optional.empty();
    }

    /** The sender's display name: of an attached original, of an inline forward, or of the mail itself. */
    private static String senderName(MailContent mail, byte[] raw) {
        try {
            var message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
            var attached = attached(message);
            if (attached != null) {
                return personal(attached);
            }
            var forwarded = FORWARDED_FROM.matcher(mail.text());
            if (forwarded.find()) {
                return forwarded.group(1).strip();
            }
            return personal(message);
        } catch (MessagingException | IOException e) {
            return null;
        }
    }

    private static String personal(MimeMessage message) throws MessagingException {
        var from = message.getFrom();
        return from != null && from.length > 0 && from[0] instanceof InternetAddress a ? a.getPersonal() : null;
    }

    private static MimeMessage attached(Part part) throws MessagingException, IOException {
        if (part.isMimeType("message/rfc822") && part.getContent() instanceof MimeMessage inner) {
            return inner;
        }
        if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                var found = attached(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // --- Import --------------------------------------------------------------------------------

    /** The inbox outcome if the mail is a statement mail; empty otherwise. One transaction for everything. */
    @Transactional
    Optional<Handled> handle(Long ownerId, MailContent mail, byte[] raw) {
        var recognised = recognise(mail, raw).orElse(null);
        if (recognised == null) {
            return Optional.empty();
        }
        var pdf = MailAttachments.firstPdf(raw).orElse(null);
        var read = recognised.fewo() && pdf != null ? FewoStatementPdf.read(pdf.content()).orElse(null) : null;
        // The PDF names the object like the booking mails do; the subject may differ.
        var propertyName = read != null ? read.object() : recognised.propertyName();
        var invoice = read != null ? read.invoiceNumber() : recognised.invoiceNumber();
        var subjectName = propertyName != null ? propertyName : STATEMENTS_MAPPING;
        var agency = agencies.resolve(recognised.agency()).orElse(null);
        var provider = agency != null ? agency.getName() : recognised.agency();

        var unit = unit(ownerId, agency, propertyName);
        if (unit.unitId() == null) {
            return Optional.of(handled(BookingMailResult.UNASSIGNED_UNIT, unit.problem(), provider, invoice,
                    subjectName, null, null));
        }
        if (invoice != null) {
            var editable = access.visibleUnits(ownerId).entrySet().stream()
                    .filter(e -> RentalAccess.canEdit(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            var existing = statementRepository.findByInvoice(editable, provider, invoice).stream().findFirst();
            if (existing.isPresent()) {
                return Optional.of(handled(BookingMailResult.ATTACHED, ALREADY_RECORDED, provider, invoice,
                        subjectName, existing.get().getId(), null));
            }
        }

        var statementDate = read != null ? read.invoiceDate() : received(mail);
        var created = statements.create(ownerId, new StatementService.StatementInput(unit.unitId(), provider,
                recognised.periodFrom(), recognised.periodTo(), statementDate, invoice,
                read != null ? read.balance() : null));
        var statementId = created.statement().getId();
        if (pdf != null) {
            var name = pdf.fileName() != null && !pdf.fileName().isBlank() ? pdf.fileName()
                    : "Abrechnung " + Objects.toString(invoice, recognised.periodFrom().toString()) + ".pdf";
            documents.store(Statement.SOURCE_TYPE, statementId, name, "application/pdf", pdf.content());
        }
        var summary = read == null ? new Summary(0, 0, 0) : settle(ownerId, statementId, unit.unitId(), read.rows());
        return Optional.of(handled(BookingMailResult.STATEMENT_CREATED, read == null ? PDF_NOT_READ : null, provider,
                invoice, subjectName, statementId, summary));
    }

    private Handled handled(BookingMailResult result, String detail, String provider, String invoice,
            String subjectName, Long statementId, Summary summary) {
        return new Handled(result.name(), detail, KIND, provider, invoice, subjectName,
                statementId == null ? null : Statement.SOURCE_TYPE, statementId, summary,
                MailRecognizer.Source.RULE.name());
    }

    private LocalDate received(MailContent mail) {
        return (mail.receivedAt() != null ? mail.receivedAt() : clock.instant()).atZone(ZONE).toLocalDate();
    }

    private record UnitLookup(Long unitId, String problem) {
    }

    /**
     * The unit by the property mapping; without a property name, the agency's „Abrechnungen“ mapping or the one unit
     * all its mappings point to (design D9). Only a unit the owner may edit.
     */
    private UnitLookup unit(Long ownerId, Agency agency, String propertyName) {
        var label = propertyName != null ? propertyName : STATEMENTS_MAPPING;
        if (agency == null) {
            return new UnitLookup(null, "Einheit für „" + label + "“ zuordnen");
        }
        Long unitId = null;
        var mapping = mappings.find(ownerId, agency.getId(), label);
        if (mapping.isPresent()) {
            unitId = mapping.get().getUnitId();
        } else if (propertyName == null) {
            var units = mappings.findByAgencyId(agency.getId()).stream()
                    .filter(m -> ownerId.equals(m.getOwnerUserId())).map(UnitMapping::getUnitId).distinct().toList();
            unitId = units.size() == 1 ? units.getFirst() : null;
        }
        if (unitId == null) {
            return new UnitLookup(null, "Einheit für „" + label + "“ zuordnen");
        }
        if (!RentalAccess.canEdit(access.role(unitId, ownerId).orElse(null))) {
            return new UnitLookup(null, "Keine Bearbeitungsrechte für die zugeordnete Einheit");
        }
        return new UnitLookup(unitId, null);
    }

    // --- Matching rows to bookings (design D6) -------------------------------------------------

    /** A row or a chain of rows of one tenant whose periods touch, with their amounts added. */
    private record Group(List<FewoStatementPdf.Row> rows) {

        LocalDate arrival() {
            return rows.getFirst().arrival();
        }

        LocalDate departure() {
            return rows.getLast().departure();
        }

        BigDecimal gross() {
            return rows.stream().map(FewoStatementPdf.Row::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal commission() {
            return rows.stream().map(FewoStatementPdf.Row::commission).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private Summary settle(Long ownerId, Long statementId, Long unitId, List<FewoStatementPdf.Row> rows) {
        var open = new ArrayList<StatementService.OpenLineInput>();
        int assigned = 0;
        for (var chain : chains(rows)) {
            var whole = new Group(chain);
            if (chain.size() > 1 && assign(ownerId, statementId, unitId, whole, open, false)) {
                assigned += chain.size();
                continue;
            }
            for (var row : chain) {
                if (assign(ownerId, statementId, unitId, new Group(List.of(row)), open, true)) {
                    assigned++;
                }
            }
        }
        if (!open.isEmpty()) {
            statements.addOpenLines(ownerId, statementId, open);
        }
        return new Summary(rows.size(), assigned, open.size());
    }

    /** Rows grouped by tenant name, each group split into chains of touching periods, in order of the PDF. */
    private static List<List<FewoStatementPdf.Row>> chains(List<FewoStatementPdf.Row> rows) {
        var byTenant = new LinkedHashMap<String, List<FewoStatementPdf.Row>>();
        rows.forEach(r -> byTenant.computeIfAbsent(r.tenant(), k -> new ArrayList<>()).add(r));
        var chains = new ArrayList<List<FewoStatementPdf.Row>>();
        for (var own : byTenant.values()) {
            var sorted = own.stream().sorted(Comparator.comparing(FewoStatementPdf.Row::arrival)).toList();
            List<FewoStatementPdf.Row> chain = null;
            for (var row : sorted) {
                if (chain != null && chain.getLast().departure().equals(row.arrival())) {
                    chain.add(row);
                } else {
                    chain = new ArrayList<>(List.of(row));
                    chains.add(chain);
                }
            }
        }
        return chains;
    }

    /**
     * Puts the group on the booking with exactly its period. Without such a booking, and only if
     * {@code openIfNone}, the group becomes an open line; a booking on another statement always makes it one.
     */
    private boolean assign(Long ownerId, Long statementId, Long unitId, Group group,
            List<StatementService.OpenLineInput> open, boolean openIfNone) {
        var candidates = bookings.findByUnitIdAndArrivalAndDeparture(unitId, group.arrival(), group.departure());
        var booking = candidates.stream().filter(b -> !b.isOwnerUse())
                .min(Comparator.comparing(Booking::isCancelled)).orElse(null);
        if (booking == null) {
            if (openIfNone) {
                open.add(openLine(group, group.rows().stream().anyMatch(FewoStatementPdf.Row::extension)
                        ? EXTENSION_NOTE : null));
            }
            return false;
        }
        var line = lines.findById(booking.getId()).orElse(null);
        if (line != null && !line.getStatementId().equals(statementId)) {
            open.add(openLine(group, ALREADY_SETTLED));
            return false;
        }
        var gross = line == null ? group.gross() : line.getGross().add(group.gross());
        var commission = line == null ? group.commission() : line.getCommission().add(group.commission());
        statements.putLine(ownerId, statementId, booking.getId(), new StatementService.LineInput(gross, commission,
                line == null ? BigDecimal.ZERO : line.getCleaning()));
        return true;
    }

    private static StatementService.OpenLineInput openLine(Group group, String note) {
        return new StatementService.OpenLineInput(group.rows().getFirst().tenant(), group.arrival(),
                group.departure(), group.gross(), group.commission(), note);
    }
}
