package app.logis.core.mail;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin's outgoing mail server (spec outgoing-mail); all addresses are invented. */
class SmtpSettingsTest extends IntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("logis", "geheim"));

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MailSender sender;

    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM core.smtp_settings");
        admin = login(createUser(Role.ADMIN, true));
    }

    private Map<String, Object> input(String password) {
        var body = new HashMap<String, Object>(Map.of("host", "localhost", "port", greenMail.getSmtp().getPort(),
                "security", "NONE", "username", "logis", "fromAddress", "logis@example.org",
                "fromName", "Logis"));
        if (password != null) {
            body.put("password", password);
        }
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions save(Map<String, Object> body, MockHttpSession s)
            throws Exception {
        return mvc.perform(put("/api/admin/smtp").with(csrf()).session(s).contentType("application/json")
                .content(json.writeValueAsString(body)));
    }

    @Test
    void configureWithConnectionCheck() throws Exception {
        getWith("/api/admin/smtp", admin).andExpect(jsonPath("$.configured").value(false));

        save(input("geheim"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.fromAddress").value("logis@example.org"));
        getWith("/api/admin/smtp", admin).andExpect(jsonPath("$.host").value("localhost"))
                .andExpect(jsonPath("$.security").value("NONE"))
                .andExpect(content().string(not(containsString("geheim"))))
                .andExpect(content().string(not(containsString("password\""))));
        var stored = jdbc.queryForObject("SELECT password_enc FROM core.smtp_settings", String.class);
        assertThat(stored).isNotEqualTo("geheim");
        assertThat(sender.configured()).isTrue();
    }

    @Test
    void wrongPasswordKeepsThePreviousSettings() throws Exception {
        save(input("geheim"), admin).andExpect(status().isOk());
        var changed = input("falsch");
        changed.put("fromName", "Neu");
        save(changed, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(SmtpSettingsService.UNREACHABLE));
        getWith("/api/admin/smtp", admin).andExpect(jsonPath("$.fromName").value("Logis"));

        var unreachable = input("geheim");
        unreachable.put("port", 1);
        save(unreachable, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(SmtpSettingsService.UNREACHABLE));
    }

    @Test
    void emptyPasswordKeepsTheStoredOne() throws Exception {
        save(input("geheim"), admin).andExpect(status().isOk());
        var changed = input(null);
        changed.put("fromName", "Logis Ferienhaus");
        save(changed, admin).andExpect(status().isOk()).andExpect(jsonPath("$.fromName").value("Logis Ferienhaus"));

        // Another server needs the password again.
        changed.put("host", "127.0.0.1");
        save(changed, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bitte das Passwort angeben."));
    }

    @Test
    void invalidSenderAddress() throws Exception {
        var body = input("geheim");
        body.put("fromAddress", "Logis <logis@example.org>");
        save(body, admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bitte eine gültige Absenderadresse angeben."));
    }

    @Test
    void testMail() throws Exception {
        postJson("/api/admin/smtp/test", Map.of("to", "admin@example.org"), admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(SmtpSettingsService.TEST_FAILED));

        save(input("geheim"), admin).andExpect(status().isOk());
        postJson("/api/admin/smtp/test", Map.of("to", "admin@example.org"), admin).andExpect(status().isNoContent());
        var received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Testmail von Logis");
        assertThat(received[0].getFrom()[0].toString()).isEqualTo("Logis <logis@example.org>");
    }

    @Test
    void onlyAdmins() throws Exception {
        var user = login(createUser(Role.USER, true));
        getWith("/api/admin/smtp", user).andExpect(status().isForbidden());
        save(input("geheim"), user).andExpect(status().isForbidden());
    }

    @Test
    void deleteTurnsSendingOff() throws Exception {
        save(input("geheim"), admin).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/smtp").with(csrf()).session(admin)).andExpect(status().isNoContent());
        assertThat(sender.configured()).isFalse();
        getWith("/api/admin/smtp", admin).andExpect(jsonPath("$.configured").value(false));
    }
}
