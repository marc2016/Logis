package app.logis.core.notifications;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** The channel „E-Mail“ (spec notifications, „Channels“ and „Short and long form“); invented values only. */
@Import(TestChannels.class)
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class MailChannelTest extends IntegrationTest {

    private static final String MAIL = "$.channels[?(@.id == 'mail')]";
    private static final Notification NOTE = new Notification("Neue Buchung zu prüfen", "Haus Waldeck",
            "Haus Waldeck\nGast: Erika Musterfrau", "/buchungen/7");

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Notifications notifications;
    @Autowired
    NotificationSettings settings;
    @Autowired
    TestChannels.Recording recording;

    @BeforeEach
    void mailServer() {
        jdbc.update("DELETE FROM core.smtp_settings");
        jdbc.update("INSERT INTO core.smtp_settings (id, host, port, security, from_address, updated_at) "
                        + "VALUES (1, 'localhost', ?, 'NONE', 'logis@example.org', ?)", greenMail.getSmtp().getPort(),
                Timestamp.from(Instant.now()));
    }

    private TestUser user(String email) {
        var user = createUser(Role.USER, true);
        var entity = users.findById(user.id()).orElseThrow();
        entity.setEmail(email);
        users.save(entity);
        settings.set(user.id(), TestChannels.TYPE, MailChannel.ID, true);
        settings.set(user.id(), TestChannels.TYPE, TestChannels.RECORDING, true);
        return user;
    }

    @Test
    void sendsTheTitleAsSubjectAndTheLongTextWithTheLink() throws Exception {
        var anna = user("anna@example.org");

        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        var mail = greenMail.getReceivedMessages()[0];
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("anna@example.org");
        assertThat(mail.getSubject()).isEqualTo("Neue Buchung zu prüfen");
        var body = GreenMailUtil.getBody(mail).replace("=\r\n", "");
        assertThat(mail.getContent().toString()).contains("Gast: Erika Musterfrau")
                .contains("In Logis öffnen: https://logis.example.org/buchungen/7");
        assertThat(body).isNotBlank();
        // The other channel got the short form.
        assertThat(recording.await(anna.id(), 1).get(0).text()).isEqualTo("Haus Waldeck");
    }

    @Test
    void withoutAnAddressOnlyTheOtherChannelIsServed() throws Exception {
        var bert = user(null);

        getWith("/api/notifications", login(bert)).andExpect(jsonPath(MAIL + ".usable").value(false))
                .andExpect(jsonPath(MAIL + ".hint").value("Keine E-Mail-Adresse hinterlegt"));
        notifications.send(TestChannels.TYPE, List.of(bert.id()), NOTE);

        recording.await(bert.id(), 1);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void withoutAServerNoConnectionIsTriedAndTheSwitchIsKept() throws Exception {
        var anna = user("anna@example.org");
        jdbc.update("DELETE FROM core.smtp_settings");

        getWith("/api/notifications", login(anna)).andExpect(jsonPath(MAIL + ".usable").value(false))
                .andExpect(jsonPath(MAIL + ".hint").value("Der E-Mail-Versand ist nicht eingerichtet"))
                .andExpect(jsonPath("$.types[?(@.key == '" + TestChannels.TYPE + "')].channels.mail.enabled")
                        .value(true));
        notifications.send(TestChannels.TYPE, List.of(anna.id()), NOTE);

        recording.await(anna.id(), 1);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
