package app.logis.core.users;

import app.logis.IntegrationTest;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Changing the own e-mail address with a code (spec authentication of outgoing-mail); invented addresses. */
class EmailChangeTest extends IntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("logis", "geheim"));

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    JdbcTemplate jdbc;

    private TestUser anna;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM core.smtp_settings");
        settings(greenMail.getSmtp().getPort());
        anna = createUser(Role.USER, true);
        var user = users.findById(anna.id()).orElseThrow();
        user.setEmail("anna@example.org");
        users.save(user);
        session = login(anna);
    }

    private void settings(int port) {
        jdbc.update("DELETE FROM core.smtp_settings");
        jdbc.update("INSERT INTO core.smtp_settings (id, host, port, security, username, password_enc, from_address, "
                        + "updated_at) VALUES (1, 'localhost', ?, 'NONE', 'logis', ?, 'logis@example.org', ?)", port,
                cipher.encrypt("geheim"), Timestamp.from(Instant.now()));
    }

    private MimeMessage[] mailsTo(String address) {
        return Arrays.stream(greenMail.getReceivedMessages()).filter(m -> {
            try {
                return m.getAllRecipients()[0].toString().equals(address);
            } catch (Exception e) {
                return false;
            }
        }).toArray(MimeMessage[]::new);
    }

    private String lastCode(String address) throws Exception {
        var mails = mailsTo(address);
        var text = (String) mails[mails.length - 1].getContent();
        var matcher = CODE.matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions request(String email) throws Exception {
        return postJson("/api/account/email", Map.of("email", email), session);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String code) throws Exception {
        return postJson("/api/account/email/confirm", Map.of("code", code), session);
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    @Test
    void changeWithCodeAndNoticeToTheOldAddress() throws Exception {
        getWith("/api/account/email", session).andExpect(jsonPath("$.email").value("anna@example.org"))
                .andExpect(jsonPath("$.canChange").value(true));

        request("anna.neu@example.org").andExpect(status().isNoContent());
        var codeMail = mailsTo("anna.neu@example.org");
        assertThat(codeMail).hasSize(1);
        assertThat(codeMail[0].getSubject()).isEqualTo("Bestätigungscode für Logis");
        getWith("/api/account/email", session).andExpect(jsonPath("$.email").value("anna@example.org"))
                .andExpect(jsonPath("$.pendingEmail").value("anna.neu@example.org"));

        confirm(lastCode("anna.neu@example.org")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna.neu@example.org"))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());

        var notice = mailsTo("anna@example.org");
        assertThat(notice).hasSize(1);
        assertThat(notice[0].getSubject()).isEqualTo("Deine E-Mail-Adresse in Logis wurde geändert.");
        var text = (String) notice[0].getContent();
        assertThat(text).contains("a…@example.org").doesNotContain("anna.neu@example.org");
    }

    @Test
    void wrongCodeAndTooManyAttempts() throws Exception {
        request("anna.neu@example.org").andExpect(status().isNoContent());
        var code = lastCode("anna.neu@example.org");
        for (int i = 0; i < 5; i++) {
            confirm(wrong(code)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Der Code ist ungültig oder abgelaufen."));
        }
        // Now even the right code is refused.
        confirm(code).andExpect(status().isBadRequest());
        getWith("/api/account/email", session).andExpect(jsonPath("$.email").value("anna@example.org"));
    }

    @Test
    void expiredCode() throws Exception {
        request("anna.neu@example.org").andExpect(status().isNoContent());
        var code = lastCode("anna.neu@example.org");
        clock.advance(Duration.ofMinutes(31));
        session = login(anna);
        confirm(code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Code ist ungültig oder abgelaufen."));
    }

    @Test
    void newRequestReplacesTheOldCode() throws Exception {
        request("anna.alt@example.org").andExpect(status().isNoContent());
        var first = lastCode("anna.alt@example.org");
        request("anna.neu@example.org").andExpect(status().isNoContent());
        var second = lastCode("anna.neu@example.org");
        if (!first.equals(second)) {
            confirm(first).andExpect(status().isBadRequest());
        }
        confirm(second).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("anna.neu@example.org"));
    }

    @Test
    void invalidAddress() throws Exception {
        request("kaputt").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bitte eine gültige E-Mail-Adresse angeben."));
    }

    @Test
    void notSetUp() throws Exception {
        jdbc.update("DELETE FROM core.smtp_settings");
        getWith("/api/account/email", session).andExpect(jsonPath("$.canChange").value(false));
        request("anna.neu@example.org").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(EmailChangeService.NOT_SET_UP));
    }

    @Test
    void failingSendKeepsNoPendingChange() throws Exception {
        settings(1);
        request("anna.neu@example.org").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(EmailChangeService.SEND_FAILED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.email_changes WHERE user_id = ?", Long.class,
                anna.id())).isZero();
    }

    @Test
    void userWithoutAddressAddsOne() throws Exception {
        var bert = createUser(Role.USER, true);
        session = login(bert);
        getWith("/api/auth/me", session).andExpect(jsonPath("$.user.emailMissing").value(true));
        request("bert@example.org").andExpect(status().isNoContent());
        confirm(lastCode("bert@example.org")).andExpect(status().isOk());
        getWith("/api/auth/me", session).andExpect(jsonPath("$.user.emailMissing").value(false));
        // No old address: no notice.
        assertThat(mailsTo("bert@example.org")).hasSize(1);
    }
}
