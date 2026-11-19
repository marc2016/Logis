package app.logis.core.mail;

import app.logis.IntegrationTest;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sending system mails (spec outgoing-mail, „Send system mails“); all addresses are invented. */
@ExtendWith(OutputCaptureExtension.class)
class SmtpMailSenderTest extends IntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("logis", "geheim"));

    @Autowired
    MailSender sender;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void noSettings() {
        jdbc.update("DELETE FROM core.smtp_settings");
    }

    private void settings(int port, String password) {
        jdbc.update("INSERT INTO core.smtp_settings (id, host, port, security, username, password_enc, from_address, "
                        + "from_name, updated_at) VALUES (1, 'localhost', ?, 'NONE', 'logis', ?, 'logis@example.org', "
                        + "'Logis Ferienhaus', ?)", port, cipher.encrypt(password), Timestamp.from(Instant.now()));
    }

    @Test
    void sendsAPlainTextMail() throws Exception {
        settings(greenMail.getSmtp().getPort(), "geheim");
        assertThat(sender.configured()).isTrue();

        sender.send("erika@example.org", "Prüfung: Grüße", "Hallo Erika,\nschöne Grüße aus Logis.");

        var received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        var mail = received[0];
        assertThat(mail.getSubject()).isEqualTo("Prüfung: Grüße");
        assertThat(mail.getFrom()[0].toString()).isEqualTo("Logis Ferienhaus <logis@example.org>");
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("erika@example.org");
        assertThat(mail.getContentType()).startsWith("text/plain").containsIgnoringCase("utf-8");
        assertThat(((String) mail.getContent()).strip()).isEqualTo("Hallo Erika,\r\nschöne Grüße aus Logis.");
    }

    @Test
    void notConfigured() {
        assertThat(sender.configured()).isFalse();
        assertThatThrownBy(() -> sender.send("erika@example.org", "Betreff", "Text"))
                .isInstanceOfSatisfying(MailSender.MailSendException.class, ex -> assertThat(ex.notConfigured()).isTrue());
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void failuresAreLoggedWithoutTextOrAddress(CapturedOutput output) {
        settings(greenMail.getSmtp().getPort(), "falsch");
        assertThatThrownBy(() -> sender.send("erika@example.org", "Geheimer Betreff", "Geheimer Text"))
                .isInstanceOfSatisfying(MailSender.MailSendException.class, ex -> assertThat(ex.notConfigured()).isFalse());

        jdbc.update("UPDATE core.smtp_settings SET port = 1");
        assertThatThrownBy(() -> sender.send("erika@example.org", "Geheimer Betreff", "Geheimer Text"))
                .isInstanceOf(MailSender.MailSendException.class);

        assertThat(output.getAll()).contains("Mail to domain example.org could not be sent")
                .doesNotContain("erika@example.org", "Geheimer");
    }
}
