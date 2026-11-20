package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import app.logis.core.users.Role;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Flags;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The booking part's sensitive values in the admin export (design D3 of admin-mail-export): rule values of recognised
 * mails and the guest data of the owner's bookings, without asking the AI. All values are invented.
 */
class MailExportValuesTest extends VacationRentalTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    /** AI is "enabled" with a stub, so that a call would be seen. */
    @MockitoBean
    AiExtractor ai;
    @Autowired
    app.logis.core.mail.Mails imports;
    @Autowired
    MailRecognizer recognizer;

    private GreenMailUser mailUser;
    private Actor anna;
    private Long unit;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        anna = actor();
        unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        mvc.perform(put("/api/mail/mailbox").with(csrf()).session(anna.session()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("host", "localhost", "port", greenMail.getImap().getPort(),
                        "username", "anna", "password", "geheim", "folder", "INBOX", "useTls", false,
                        "cutoffDate", "2026-11-01", "enabled", true)))).andExpect(status().isOk());
        mvc.perform(put("/api/mail/mailbox/export-consent").with(csrf()).session(anna.session())
                .contentType("application/json").content("{\"allowed\":true}")).andExpect(status().isOk());
    }

    private void deliver(byte[] eml) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(eml));
        greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                .appendMessage(message, new Flags(), Date.from(clock.instant()));
    }

    private static byte[] sample(String name) throws Exception {
        try (var in = MailExportValuesTest.class.getResourceAsStream("/mails/" + name)) {
            return in.readAllBytes();
        }
    }

    private List<Map<String, Object>> inbox() throws Exception {
        return JsonPath.read(getWith("/api/mail/inbox", anna.session()).andReturn().getResponse()
                .getContentAsString(), "$");
    }

    private Map<String, byte[]> export() throws Exception {
        var admin = login(createUser(Role.ADMIN, true));
        var zip = postJson("/api/admin/mail-export", Map.of("owners", List.of(anna.id())), admin)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var files = new LinkedHashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    @Test
    void guestValuesAreReplacedAndTheConfirmationIsStillRecognised() throws Exception {
        // A recognised confirmation that becomes a booking with guest data ...
        deliver(sample("fewo-confirmation-adults.eml"));
        imports.pollNow(anna.id());
        var confirmation = ((Number) inbox().getFirst().get("id")).longValue();
        postJson("/api/vacationrental/mail/" + confirmation + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        // ... and a mail no rule knows, from the guest's own domain, that mentions the guest and the booking.
        deliver(("From: Erika Musterfrau <erika@musterfrau.example>\r\nTo: anna@example.org\r\n"
                + "Subject: Frage zu Buchung 250905\r\nMessage-ID: <frage-1@musterfrau.example>\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                + "Hallo, hier ist Erika Musterfrau. Wir kommen am 06.11.2026 erst abends. Tel. 0151 0000001\r\n")
                .getBytes(StandardCharsets.UTF_8));
        imports.pollNow(anna.id());
        assertThat(inbox()).hasSize(2);

        clearInvocations(ai);
        var files = export();
        verifyNoInteractions(ai);

        assertThat(files).hasSize(3);
        var all = new String(String.join("\n", files.values().stream()
                .map(b -> new String(b, StandardCharsets.UTF_8)).toList()));
        assertThat(all).doesNotContain("Erika", "Musterfrau", "250905", "0151 0000001", "erika@example.org",
                "Musterweg 1", "12345")
                .contains("06.11.2026");

        // The pseudonymized confirmation is still recognised by the rules, with the replacement values.
        var exported = files.entrySet().stream().filter(e -> e.getKey().contains("booking-created")).findFirst()
                .orElseThrow().getValue();
        var parsed = recognizer.recognizeByRules(MailContent.parse(exported)).orElseThrow();
        assertThat(parsed.agency()).isEqualTo("Fewo & Meer");
        assertThat(parsed.guest().name()).isEqualTo("Gast 1");
        assertThat(parsed.bookingRef()).matches("\\d{6}").isNotEqualTo("250905");
        assertThat(parsed.arrival()).hasToString("2026-11-06");
    }
}
