package app.logis.core.mail;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consent, filters, access, content and log of the admin mail export (specs/mail-export); invented values only. */
class MailExportTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private record Owner(TestUser user, MockHttpSession session) {
    }

    private Owner ownerWithMailbox(boolean consent) throws Exception {
        var user = createUser(Role.USER, true);
        jdbc.update("INSERT INTO core.mailboxes (owner_user_id, host, port, username, password_enc, cutoff_date) "
                + "VALUES (?, 'imap.example.org', 993, ?, 'enc', DATE '2026-01-01')", user.id(), user.username());
        var session = login(user);
        if (consent) {
            consent(session, true);
        }
        return new Owner(user, session);
    }

    private void consent(MockHttpSession session, boolean allowed) throws Exception {
        mvc.perform(put("/api/mail/mailbox/export-consent").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":" + allowed + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminExportAllowed").value(allowed));
    }

    private static String eml(String subject, String body, String from) {
        return "From: " + from + "\r\nTo: vermieter@example.net\r\nSubject: " + subject
                + "\r\nDate: Tue, 03 Nov 2026 10:00:00 +0100\r\nMessage-ID: <" + UUID.randomUUID()
                + "@agentur.example>\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n\r\n" + body + "\r\n";
    }

    private Long mail(Owner owner, String received, String result, String source, String eml) {
        // As stored by the inbox: a handler is set when a part of Logis took the mail.
        var handler = result.equals("NOT_RECOGNISED") || result.equals("DELETED") ? null : "test.stub";
        return jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, sender, subject, "
                        + "raw, result, source, handler) VALUES (?, ?, ?, 'buchung@agentur.example', 'betreff', ?, ?, ?, ?) "
                        + "RETURNING id", Long.class,
                owner.user().id(), UUID.randomUUID().toString(), Timestamp.from(Instant.parse(received)),
                eml.getBytes(StandardCharsets.UTF_8), result, source, handler);
    }

    private Map<String, String> unzip(byte[] zip) throws Exception {
        var files = new LinkedHashMap<String, String>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private org.springframework.test.web.servlet.ResultActions export(MockHttpSession admin, Map<String, Object> filter)
            throws Exception {
        return postJson("/api/admin/mail-export", filter, admin);
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void consentIsOffByDefaultAndOnlyTheOwnerChangesIt() throws Exception {
        var anna = ownerWithMailbox(false);
        getWith("/api/mail/mailbox", anna.session()).andExpect(jsonPath("$.adminExportAllowed").value(false))
                .andExpect(jsonPath("$.lastExport").doesNotExist());
        consent(anna.session(), true);
        consent(anna.session(), false);

        // An admin has no endpoint for another user's consent; their own call only concerns their own mailbox.
        var admin = login(createUser(Role.ADMIN, true));
        mvc.perform(put("/api/mail/mailbox/export-consent").with(csrf()).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}")).andExpect(status().isNotFound());
        getWith("/api/mail/mailbox", anna.session()).andExpect(jsonPath("$.adminExportAllowed").value(false));
    }

    @Test
    void onlyConsentingMailboxesAreOffered() throws Exception {
        var anna = ownerWithMailbox(true);
        var bert = ownerWithMailbox(false);
        var admin = login(createUser(Role.ADMIN, true));

        var mailboxes = body(getWith("/api/admin/mail-export/mailboxes", admin).andExpect(status().isOk()));
        assertThat(mailboxes.findValuesAsText("username")).contains(anna.user().username())
                .doesNotContain(bert.user().username());

        export(admin, Map.of("owners", List.of(bert.user().id()))).andExpect(status().isNotFound());
        export(admin, Map.of("owners", List.of(anna.user().id(), bert.user().id()))).andExpect(status().isNotFound());

        consent(anna.session(), false);
        export(admin, Map.of("owners", List.of(anna.user().id()))).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdmins() throws Exception {
        var anna = ownerWithMailbox(true);
        export(anna.session(), Map.of("owners", List.of(anna.user().id()))).andExpect(status().isForbidden());
        getWith("/api/admin/mail-export/mailboxes", anna.session()).andExpect(status().isForbidden());
    }

    @Test
    void restoredAdminSessionNeedsAFreshSignIn() throws Exception {
        var admin = createUser(Role.ADMIN, true);
        var device = new Device(FIREFOX_LINUX).signIn(admin, true);
        device.loseSession();
        assertStatus(device.get("/api/admin/mail-export/mailboxes"), 403);
    }

    @Test
    void filtersCombineAndDeletedMailsAreLeftOut() throws Exception {
        var anna = ownerWithMailbox(true);
        var from = "\"Agentur\" <buchung@agentur.example>";
        mail(anna, "2026-11-03T09:00:00Z", "NOT_RECOGNISED", "Fewo & Meer", eml("A", "eins", from));
        mail(anna, "2026-11-03T10:00:00Z", "CREATED", "Fewo & Meer", eml("B", "zwei", from));
        mail(anna, "2026-11-04T10:00:00Z", "NOT_RECOGNISED", "Holidu", eml("C", "drei", from));
        mail(anna, "2026-10-01T10:00:00Z", "NOT_RECOGNISED", "Fewo & Meer", eml("D", "vier", from));
        mail(anna, "2026-11-03T11:00:00Z", "DELETED", "Fewo & Meer", "");
        var admin = login(createUser(Role.ADMIN, true));

        var agencies = body(getWith("/api/admin/mail-export/agencies?owners=" + anna.user().id(), admin));
        assertThat(agencies.toString()).isEqualTo("[\"Fewo & Meer\",\"Holidu\"]");

        var zip = export(admin, Map.of("owners", List.of(anna.user().id()), "from", "2026-11-01", "to", "2026-11-30",
                "results", List.of("NOT_RECOGNISED"), "sources", List.of("Fewo & Meer")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(zip.getContentType()).isEqualTo("application/zip");
        assertThat(zip.getHeader("Content-Disposition")).startsWith("attachment; filename=\"logis-mails-");
        var files = unzip(zip.getContentAsByteArray());
        assertThat(files.keySet()).containsExactly("0001-not-recognised-fewo-meer.eml", "index.csv");
        assertThat(files.get("0001-not-recognised-fewo-meer.eml")).contains("Subject: A", "eins");

        var index = files.get("index.csv").lines().toList();
        assertThat(index).hasSize(2);
        assertThat(index.get(0)).isEqualTo("﻿Datei;Eingang;Absender;Betreff;Ergebnis;Art;Vermittler;Postfach");
        assertThat(index.get(1)).isEqualTo("0001-not-recognised-fewo-meer.eml;2026-11-03T10:00:00;"
                + "Agentur <buchung@agentur.example>;A;Nicht erkannt;;Fewo & Meer;" + anna.user().username());

        // The agency's address stays because Logis recognised mails from its domain; an unknown sender's does not.
        mail(anna, "2026-11-05T10:00:00Z", "NOT_RECOGNISED", null, eml("E", "fünf", "<info@unbekannt.example>"));

        // Without filters: all but the deleted mail.
        var all = unzip(export(admin, Map.of("owners", List.of(anna.user().id()))).andReturn().getResponse()
                .getContentAsByteArray());
        assertThat(all).hasSize(6);
        assertThat(String.join("\n", all.values())).contains("buchung@agentur.example")
                .doesNotContain("info@unbekannt.example");
    }

    @Test
    void knownGuestValuesDoNotLeave() throws Exception {
        var anna = ownerWithMailbox(true);
        mail(anna, "2026-11-03T09:00:00Z", "NOT_RECOGNISED", null, eml("Anfrage von max.beispiel@example.net",
                "Bitte rufen Sie mich an: 0170 1234567. IBAN DE89 3704 0044 0532 0130 00. Anreise 23.12.2026.",
                "Max Beispiel <max.beispiel@example.net>"));
        var admin = login(createUser(Role.ADMIN, true));

        var files = unzip(export(admin, Map.of("owners", List.of(anna.user().id()))).andReturn().getResponse()
                .getContentAsByteArray());
        var all = String.join("\n", files.values());
        assertThat(all).doesNotContain("max.beispiel@example.net", "0170 1234567", "3704 0044")
                .contains("Anreise 23.12.2026", "person1@example.org");
    }

    @Test
    void tooManyMails() throws Exception {
        var anna = ownerWithMailbox(true);
        jdbc.update("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "SELECT ?, 'bulk-' || g, now(), ''::bytea, 'NOT_RECOGNISED' FROM generate_series(1, 2001) g",
                anna.user().id());
        var admin = login(createUser(Role.ADMIN, true));
        export(admin, Map.of("owners", List.of(anna.user().id()))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(MailExportService.TOO_MANY));
    }

    @Test
    void exportsAreLoggedAndShownToTheOwner() throws Exception {
        var anna = ownerWithMailbox(true);
        mail(anna, "2026-11-03T09:00:00Z", "NOT_RECOGNISED", null, eml("A", "eins", "<buchung@agentur.example>"));
        mail(anna, "2026-11-04T09:00:00Z", "NOT_RECOGNISED", null, eml("B", "zwei", "<buchung@agentur.example>"));
        var adminUser = createUser(Role.ADMIN, true);
        var admin = login(adminUser);

        export(admin, Map.of("owners", List.of(anna.user().id()), "results", List.of("NOT_RECOGNISED")))
                .andExpect(status().isOk());

        getWith("/api/mail/mailbox", anna.session())
                .andExpect(jsonPath("$.lastExport.mailCount").value(2))
                .andExpect(jsonPath("$.lastExport.adminName").value("Test " + adminUser.username()))
                .andExpect(jsonPath("$.lastExport.at").exists());
        var log = body(getWith("/api/admin/mail-export/log", admin));
        var entry = log.get(0);
        assertThat(entry.get("ownerUserId").asLong()).isEqualTo(anna.user().id());
        assertThat(entry.get("mailCount").asInt()).isEqualTo(2);
        assertThat(entry.get("filters").asText()).contains("NOT_RECOGNISED");
    }
}
