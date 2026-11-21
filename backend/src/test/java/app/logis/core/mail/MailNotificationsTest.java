package app.logis.core.mail;

import app.logis.IntegrationTest;
import app.logis.core.notifications.TestChannels;
import app.logis.core.users.Role;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
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
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications of the inbox and the event for new mails (spec mail-inbox, „Notify about a failing fetch“ and
 * „Notify about mails that need attention“; design D6 of notifications-pushover). A stub handler takes mails whose
 * subject starts with "Stub" and reports those starting with "Stub offen" as waiting.
 */
@Import({MailNotificationsTest.Config.class, TestChannels.class})
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class MailNotificationsTest extends IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Order(0)
        MailHandler stubHandler() {
            return new MailHandler() {
                public String key() {
                    return "test.stub";
                }

                public Optional<Handled> handle(Long owner, MailContent mail, byte[] raw) {
                    if (!mail.subject().startsWith("Stub")) {
                        return Optional.empty();
                    }
                    var result = mail.subject().startsWith("Stub offen") ? "WAITING" : "TAKEN";
                    return Optional.of(new Handled(result, null, "TEST", "Tester", "1", "Objekt", "test.record", 7L,
                            null, "RULE"));
                }

                public Set<String> attentionResults() {
                    return Set.of("WAITING");
                }
            };
        }

        @Bean
        Imported imported() {
            return new Imported();
        }
    }

    static class Imported {

        final List<MailImported> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(MailImported event) {
            events.add(event);
        }

        List<MailImported> of(Long userId) {
            return events.stream().filter(e -> e.ownerUserId().equals(userId)).toList();
        }
    }

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    @Autowired
    MailService service;
    @Autowired
    Mails mails;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TestChannels.Recording recording;
    @Autowired
    TestChannels.LongRecording longRecording;
    @Autowired
    Imported imported;

    private GreenMailUser mailUser;
    private Long userId;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        var user = createUser(Role.USER, true);
        userId = user.id();
        session = login(user);
        var body = Map.of("host", "localhost", "port", greenMail.getImap().getPort(), "username", "anna",
                "password", "geheim", "folder", "INBOX", "useTls", false, "cutoffDate", "2026-01-01", "enabled", true);
        mvc.perform(put("/api/mail/mailbox").with(csrf()).session(session).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
        TestChannels.switchOn(jdbc, userId, "core.mailAttention", "core.mailFetchFailed");
    }

    private void deliver(String subject, Instant receivedAt) throws Exception {
        var eml = ("From: sender@example.org\r\nSubject: " + subject + "\r\nMessage-ID: <" + subject.hashCode() + "."
                + System.nanoTime() + "@example.org>\r\n\r\nHallo").getBytes(StandardCharsets.UTF_8);
        greenMail.getManagers().getImapHostManager().getInbox(mailUser).appendMessage(
                new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(eml)), new Flags(),
                Date.from(receivedAt == null ? clock.instant() : receivedAt));
    }

    @Test
    void oneNotificationPerPollNamesTheNumberAndOnlyTheLongFormNamesSendersAndSubjects() throws Exception {
        deliver("Unbekannt eins", null);
        service.pollIfIdle(userId);

        var first = recording.await(userId, 1).get(0);
        assertThat(first.title()).isEqualTo("1 E-Mail braucht Aufmerksamkeit");
        assertThat(first.link()).isEqualTo("https://logis.example.org/posteingang?ergebnis=offen");
        assertThat(first.text()).doesNotContain("sender@example.org").doesNotContain("Unbekannt eins");
        assertThat(longRecording.await(userId, 1).get(0).text()).contains("– sender@example.org: Unbekannt eins");

        deliver("Unbekannt zwei", null);
        deliver("Stub offen drei", null);
        deliver("Unbekannt vier", null);
        deliver("Stub erledigt", null);
        mails.pollNow(userId);

        var second = recording.await(userId, 2).get(1);
        assertThat(second.title()).isEqualTo("3 E-Mails brauchen Aufmerksamkeit");
        assertThat(second.text()).contains("3 E-Mails").doesNotContain("Unbekannt").doesNotContain("Stub");
        assertThat(longRecording.await(userId, 2).get(1).text()).contains("Unbekannt zwei")
                .contains("Stub offen drei").contains("Unbekannt vier").doesNotContain("Stub erledigt");

        // What the notification counts is what the inbox marks.
        getWith("/api/mail/inbox", session)
                .andExpect(jsonPath("$[?(@.subject == 'Stub offen drei')].needsAttention").value(true))
                .andExpect(jsonPath("$[?(@.subject == 'Unbekannt eins')].needsAttention").value(true))
                .andExpect(jsonPath("$[?(@.subject == 'Stub erledigt')].needsAttention").value(false));
    }

    @Test
    void handledMailsOlderFetchesOldMailsAndReprocessingStayQuiet() throws Exception {
        deliver("Stub erledigt", null);
        mails.pollNow(userId);
        assertThat(imported.of(userId)).singleElement().satisfies(event -> {
            assertThat(event.handlerKey()).isEqualTo("test.stub");
            assertThat(event.result()).isEqualTo("TAKEN");
            assertThat(event.linkType()).isEqualTo("test.record");
            assertThat(event.linkId()).isEqualTo(7L);
        });

        // Received three days ago: fetched by the poll, but not new.
        deliver("Unbekannt alt", clock.instant().minus(Duration.ofDays(3)));
        mails.pollNow(userId);
        // Fetched as older mails.
        jdbc.update("UPDATE core.mailboxes SET cutoff_date = ? WHERE owner_user_id = ?",
                LocalDate.now().plusDays(1), userId);
        deliver("Unbekannt nachgeholt", null);
        mails.fetchOlder(userId, LocalDate.of(2026, 1, 1));
        // Processed again.
        var id = jdbc.queryForObject("SELECT id FROM core.mails WHERE owner_user_id = ? AND subject = ?", Long.class,
                userId, "Unbekannt nachgeholt");
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), session).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.mails WHERE owner_user_id = ?", Integer.class,
                userId)).isEqualTo(3);
        recording.await(userId, 0);
        assertThat(imported.of(userId)).hasSize(1);
    }

    @Test
    void aFailingRegularPollNotifiesOnceUntilItSucceededAgain() throws Exception {
        var port = greenMail.getImap().getPort();
        service.pollIfIdle(userId);
        recording.await(userId, 0);

        jdbc.update("UPDATE core.mailboxes SET port = 1 WHERE owner_user_id = ?", userId);
        service.pollIfIdle(userId);
        var failed = recording.await(userId, 1).get(0);
        assertThat(failed.title()).isEqualTo("Postfach-Abruf fehlgeschlagen");
        assertThat(failed.link()).isEqualTo("https://logis.example.org/posteingang");
        assertThat(failed.text()).doesNotContain("geheim").doesNotContainIgnoringCase("connect")
                .doesNotContain("localhost");
        assertThat(longRecording.await(userId, 1).get(0).text()).doesNotContain("geheim")
                .doesNotContainIgnoringCase("connect");

        // Still failing: no second notification.
        service.pollIfIdle(userId);
        service.pollIfIdle(userId);
        recording.await(userId, 1);

        jdbc.update("UPDATE core.mailboxes SET port = ? WHERE owner_user_id = ?", port, userId);
        service.pollIfIdle(userId);
        jdbc.update("UPDATE core.mailboxes SET port = 1 WHERE owner_user_id = ?", userId);
        service.pollIfIdle(userId);
        recording.await(userId, 2);
    }

    @Test
    void aFetchTheUserStartsShowsItsErrorOnThePageAndDoesNotNotify() throws Exception {
        service.pollIfIdle(userId);
        jdbc.update("UPDATE core.mailboxes SET port = 1 WHERE owner_user_id = ?", userId);

        mails.pollNow(userId);

        getWith("/api/mail/mailbox", session).andExpect(jsonPath("$.lastError").exists());
        recording.await(userId, 0);
    }
}
