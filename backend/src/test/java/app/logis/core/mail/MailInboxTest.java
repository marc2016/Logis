package app.logis.core.mail;

import app.logis.IntegrationTest;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The core inbox with a stub handler that takes mails whose subject starts with "Stub". */
@Import(MailInboxTest.StubHandlerConfig.class)
class MailInboxTest extends IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class StubHandlerConfig {
        @Bean
        @Order(0)
        MailHandler stubHandler() {
            return new MailHandler() {
                public String key() {
                    return "test.stub";
                }

                public Optional<Handled> handle(Long owner, MailContent mail, byte[] raw) {
                    return mail.subject().startsWith("Stub")
                            ? Optional.of(new Handled("TAKEN", null, "TEST", "Tester", "1", "Objekt", null, null,
                                    Map.of("n", 1), "RULE"))
                            : Optional.empty();
                }
            };
        }
    }

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    @Autowired
    Mails mails;
    @Autowired
    JdbcTemplate jdbc;

    private GreenMailUser mailUser;
    private Long userId;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        var user = createUser(Role.USER, true);
        userId = user.id();
        session = login(user);
        saveMailbox(session, "geheim").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions saveMailbox(MockHttpSession s, String password)
            throws Exception {
        var body = new HashMap<String, Object>(Map.of("host", "localhost", "port", greenMail.getImap().getPort(),
                "username", "anna", "folder", "INBOX", "useTls", false, "cutoffDate", "2026-11-01",
                "enabled", true));
        if (password != null) {
            body.put("password", password);
        }
        return mvc.perform(put("/api/mail/mailbox").with(csrf()).session(s)
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private void deliver(String subject, String receivedAt) throws Exception {
        var eml = ("From: sender@example.org\r\nSubject: " + subject + "\r\nMessage-ID: <" + subject.hashCode() + "."
                + System.nanoTime() + "@example.org>\r\n\r\nHallo").getBytes(StandardCharsets.UTF_8);
        greenMail.getManagers().getImapHostManager().getInbox(mailUser).appendMessage(
                new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(eml)), new Flags(),
                Date.from(receivedAt == null ? clock.instant() : Instant.parse(receivedAt)));
    }

    private List<Map<String, Object>> inbox(MockHttpSession s) throws Exception {
        return JsonPath.read(getWith("/api/mail/inbox", s).andReturn().getResponse().getContentAsString(), "$");
    }

    @Test
    void mailboxSettingsNeverReturnThePassword() throws Exception {
        getWith("/api/mail/mailbox", session)
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(content().string(not(containsString("geheim"))));
        saveMailbox(session, "falsch")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen"));
        saveMailbox(session, null).andExpect(status().isOk());
    }

    @Test
    void handlersCutOffAndDedup() throws Exception {
        deliver("Stub eins", "2026-11-05T10:00:00Z");
        deliver("Unbekannt", "2026-11-05T10:00:00Z");
        deliver("Stub alt", "2025-09-01T10:00:00Z");
        mails.pollNow(userId);
        var items = inbox(session);
        assertThat(items).hasSize(2);
        var taken = items.stream().filter(m -> "Stub eins".equals(m.get("subject"))).findFirst().orElseThrow();
        assertThat(taken.get("result")).isEqualTo("TAKEN");
        assertThat(taken.get("handler")).isEqualTo("test.stub");
        assertThat(taken.get("source")).isEqualTo("Tester");
        var unknown = items.stream().filter(m -> "Unbekannt".equals(m.get("subject"))).findFirst().orElseThrow();
        assertThat(unknown.get("result")).isEqualTo("NOT_RECOGNISED");

        mails.pollNow(userId);
        assertThat(inbox(session)).hasSize(2);
        mails.fetchOlder(userId, LocalDate.of(2025, 1, 1));
        assertThat(inbox(session)).hasSize(3);
        for (var m : greenMail.getReceivedMessages()) {
            assertThat(m.getFlags().contains(Flags.Flag.SEEN)).isFalse();
        }
    }

    @Test
    void retryIgnoreAndDeleteKeepsTheMessageId() throws Exception {
        deliver("Unbekannt", "2026-11-05T10:00:00Z");
        mails.pollNow(userId);
        var id = ((Number) inbox(session).getFirst().get("id")).longValue();
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_RECOGNISED"));
        postJson("/api/mail/inbox/" + id + "/ignore", Map.of(), session)
                .andExpect(jsonPath("$.result").value("IGNORED"));
        getWith("/api/mail/inbox/" + id + "/text", session).andExpect(jsonPath("$.text").value("Hallo"));
        mvc.perform(delete("/api/mail/inbox/" + id).with(csrf()).session(session))
                .andExpect(status().isNoContent());
        assertThat(inbox(session)).isEmpty();
        mails.fetchOlder(userId, LocalDate.of(2026, 11, 1));
        assertThat(inbox(session)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT length(raw) FROM core.mails WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void inboxIsPrivateAndErrorsAreRecorded() throws Exception {
        deliver("Unbekannt", "2026-11-05T10:00:00Z");
        mails.pollNow(userId);
        var id = ((Number) inbox(session).getFirst().get("id")).longValue();
        var bert = login(createUser(Role.USER, true));
        assertThat(inbox(bert)).isEmpty();
        getWith("/api/mail/inbox/" + id + "/text", bert).andExpect(status().isNotFound());

        jdbc.update("UPDATE core.mailboxes SET port = 1 WHERE owner_user_id = ?", userId);
        mails.pollNow(userId);
        getWith("/api/mail/mailbox", session)
                .andExpect(jsonPath("$.lastError").value(containsString("Abruf fehlgeschlagen")));
    }
}
