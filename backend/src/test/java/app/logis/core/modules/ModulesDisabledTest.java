package app.logis.core.modules;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "logis.modules.vacationrental.enabled=false")
class ModulesDisabledTest extends IntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    app.logis.core.notifications.Notifications notifications;


    @Test
    void disabledModuleIsNotListed() throws Exception {
        mvc.perform(get("/api/modules").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'vacationrental')]").isEmpty());
    }

    @Test
    void disabledModuleEndpointGives404() throws Exception {
        var session = login(createUser(app.logis.core.users.Role.USER, true));
        getWith("/api/vacationrental/bookings", session).andExpect(status().isNotFound());
        getWith("/api/vacationrental/occupancy/overview?unitId=1&year=2026", session)
                .andExpect(status().isNotFound());
    }

    /** Spec notifications, „Event types“: types of a disabled module are not offered, switches for them are kept. */
    @Test
    void eventTypesOfTheDisabledModuleAreNotOfferedNothingIsSentAndSwitchesSurvive() throws Exception {
        var user = createUser(app.logis.core.users.Role.USER, true);
        jdbc.update("INSERT INTO core.notification_settings (user_id, event_type, channel, enabled, updated_at) "
                + "VALUES (?, 'vacationrental.bookingReview', 'mail', true, now())", user.id());
        var session = login(user);

        getWith("/api/notifications", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.types[?(@.group == 'Ferienvermietung')]").isEmpty())
                .andExpect(jsonPath("$.types[?(@.key == 'core.signIn')]").isNotEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/notifications/vacationrental.bookingReview/mail")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .session(session).contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        // Raising it does nothing and does not fail.
        notifications.send("vacationrental.bookingReview", java.util.List.of(user.id()),
                new app.logis.core.notifications.Notification("Titel", "kurz", "lang", null));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT enabled FROM "
                + "core.notification_settings WHERE user_id = ? AND event_type = 'vacationrental.bookingReview'",
                Boolean.class, user.id())).isTrue();
    }
}
