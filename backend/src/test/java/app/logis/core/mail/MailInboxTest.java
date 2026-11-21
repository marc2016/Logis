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
    @Autowired
    MailService service;

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

    @Test
    void fetchStatusShowsProgressOfTheOwnFetchOnly() throws Exception {
        var bert = login(createUser(Role.USER, true));
        getWith("/api/mail/mailbox/fetch-status", session).andExpect(status().isNoContent());
        deliver("Stub eins", "2026-11-05T10:00:00Z");
        deliver("Unbekannt", "2026-11-06T10:00:00Z");
        mails.pollNow(userId);
        deliver("Stub alt", "2025-09-01T10:00:00Z");
        mails.fetchOlder(userId, LocalDate.of(2025, 1, 1));
        getWith("/api/mail/mailbox/fetch-status", session)
                .andExpect(jsonPath("$.kind").value("OLDER"))
                .andExpect(jsonPath("$.from").value("2025-01-01"))
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.checked").value(3))
                .andExpect(jsonPath("$.created").value(1));
        getWith("/api/mail/mailbox/fetch-status", bert).andExpect(status().isNoContent());
    }

    @Test
    void onlyOneFetchRunsAtATime() throws Exception {
        deliver("Stub eins", "2026-11-05T10:00:00Z");
        var run = service.beginOlder(userId, LocalDate.of(2025, 1, 1));
        postJson("/api/mail/mailbox/poll", Map.of(), session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Es läuft bereits ein Abruf."));
        postJson("/api/mail/mailbox/fetch-older", Map.of("from", "2025-01-01"), session)
                .andExpect(status().isConflict());
        getWith("/api/mail/mailbox/fetch-status", session).andExpect(jsonPath("$.state").value("RUNNING"));
        // The scheduled poll skips the busy mailbox instead of failing.
        service.pollIfIdle(userId);
        assertThat(inbox(session)).isEmpty();

        service.run(run);
        assertThat(inbox(session)).hasSize(1);
        getWith("/api/mail/mailbox/fetch-status", session).andExpect(jsonPath("$.state").value("DONE"));
    }

    @Test
    void cutOffDayStartsAtMidnightInGermanyNotInUtc() throws Exception {
        // 00:20 in Germany on 21 November is still 20 November in UTC, the server's clock.
        clock.advance(java.time.Duration.between(clock.instant(), Instant.parse("2026-11-20T23:20:00Z")));
        var body = new HashMap<String, Object>(Map.of("host", "localhost", "port", greenMail.getImap().getPort(),
                "username", "anna", "folder", "INBOX", "useTls", false, "cutoffDate", "2026-11-21", "enabled", true));
        mvc.perform(put("/api/mail/mailbox").with(csrf()).session(session)
                .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        deliver("Kurz nach Mitternacht", "2026-11-20T23:10:00Z");
        deliver("Vor Mitternacht", "2026-11-20T22:50:00Z");
        mails.pollNow(userId);
        assertThat(inbox(session)).extracting(m -> m.get("subject")).containsExactly("Kurz nach Mitternacht");
        // Today in Germany is not in the future, although the server's day is still the day before.
        postJson("/api/mail/mailbox/fetch-older", Map.of("from", "2026-11-21"), session).andExpect(status().isAccepted());
    }

    @Test
    void olderMailsCannotBeFetchedFromTheFuture() throws Exception {
        var today = LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Europe/Berlin"));
        postJson("/api/mail/mailbox/fetch-older", Map.of("from", today.plusDays(1).toString()), session)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Datum darf nicht in der Zukunft liegen."));
    }

    @Test
    void theSameMailIsStoredOnceEvenWhenFetchedInParallel() throws Exception {
        var raw = "From: sender@example.org\r\nSubject: Doppelt\r\nMessage-ID: <doppelt@example.org>\r\n\r\nHallo"
                .getBytes(StandardCharsets.UTF_8);
        var mail = new ImapClient.FetchedMail("<doppelt@example.org>", Instant.parse("2026-11-05T10:00:00Z"), raw);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return service.process(userId, mail);
                }));
            }
            start.countDown();
            int stored = 0;
            for (var r : results) {
                stored += r.get() ? 1 : 0;
            }
            assertThat(stored).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(inbox(session)).hasSize(1);
    }
}
