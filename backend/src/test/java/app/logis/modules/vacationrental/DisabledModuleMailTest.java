package app.logis.modules.vacationrental;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With vacation rental disabled, booking mails are not handled; the core still lists them. */
@TestPropertySource(properties = "logis.modules.vacationrental.enabled=false")
class DisabledModuleMailTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void bookingMailStaysNotRecognised() throws Exception {
        var user = createUser(Role.USER, true);
        var session = login(user);
        byte[] raw;
        try (var in = getClass().getResourceAsStream("/mails/fewo-confirmation-adults.eml")) {
            raw = in.readAllBytes();
        }
        var id = jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "VALUES (?, '<x@example.org>', now(), ?, 'NOT_RECOGNISED') RETURNING id", Long.class, user.id(), raw);
        postJson("/api/mail/inbox/" + id + "/retry", java.util.Map.of(), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_RECOGNISED"))
                .andExpect(jsonPath("$.handler").doesNotExist());
    }
}
