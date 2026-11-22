package app.logis.core.notifications;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The switches under „Benachrichtigungen“ (spec notifications: choose per type and channel, nothing unasked). */
@Import(TestChannels.class)
class NotificationSettingsTest extends IntegrationTest {

    private static final String TYPE = "$.types[?(@.key == '" + TestChannels.TYPE + "')]";
    private static final String MANDATORY = "$.types[?(@.key == '" + TestChannels.MANDATORY_TYPE + "')]";

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions set(MockHttpSession session, String type, String channel, String body) throws Exception {
        return mvc.perform(put("/api/notifications/" + type + "/" + channel).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void everythingIsOffExceptMandatoryChannels() throws Exception {
        var anna = login(createUser(Role.USER, true));
        getWith("/api/notifications", anna).andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[?(@.id == 'recording')].label").value("Aufzeichnung"))
                .andExpect(jsonPath("$.channels[?(@.id == 'recording')].usable").value(true))
                .andExpect(jsonPath(TYPE + ".label").value("Testereignis"))
                .andExpect(jsonPath(TYPE + ".group").value("Test"))
                .andExpect(jsonPath(TYPE + ".channels.recording.enabled").value(false))
                .andExpect(jsonPath(TYPE + ".channels.recording.mandatory").value(false))
                .andExpect(jsonPath(TYPE + ".channels.mail.enabled").value(false))
                .andExpect(jsonPath(MANDATORY + ".channels.recording.enabled").value(true))
                .andExpect(jsonPath(MANDATORY + ".channels.recording.mandatory").value(true));
    }

    @Test
    void aSwitchIsSavedAtOnceAndOnlyForItsUser() throws Exception {
        var annaUser = createUser(Role.USER, true);
        var anna = login(annaUser);
        var bert = login(createUser(Role.USER, true));

        set(anna, TestChannels.TYPE, "recording", "{\"enabled\":true}").andExpect(status().isNoContent());

        // A new session of the same user, as on another device.
        getWith("/api/notifications", login(annaUser))
                .andExpect(jsonPath(TYPE + ".channels.recording.enabled").value(true));
        getWith("/api/notifications", bert).andExpect(jsonPath(TYPE + ".channels.recording.enabled").value(false));

        set(anna, TestChannels.TYPE, "recording", "{\"enabled\":false}").andExpect(status().isNoContent());
        getWith("/api/notifications", anna).andExpect(jsonPath(TYPE + ".channels.recording.enabled").value(false));
    }

    @Test
    void unknownTypesAndChannelsAndSwitchingOffAMandatoryChannelAreRejected() throws Exception {
        var anna = login(createUser(Role.USER, true));
        set(anna, "vacationrental.nothing", "recording", "{\"enabled\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Einstellung ist ungültig."));
        set(anna, TestChannels.TYPE, "carrier-pigeon", "{\"enabled\":true}").andExpect(status().isBadRequest());
        set(anna, TestChannels.TYPE, "recording", "{}").andExpect(status().isBadRequest());
        set(anna, TestChannels.MANDATORY_TYPE, "recording", "{\"enabled\":false}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Diese Benachrichtigung kann nicht abgeschaltet werden."));
        getWith("/api/notifications", anna)
                .andExpect(jsonPath(MANDATORY + ".channels.recording.enabled").value(true));
    }

    @Test
    void switchesOfATypeThatIsNotRegisteredAreKeptAndNotOffered() throws Exception {
        var user = createUser(Role.USER, true);
        jdbc.update("INSERT INTO core.notification_settings (user_id, event_type, channel, enabled, updated_at) "
                + "VALUES (?, 'othermodule.event', 'recording', true, now())", user.id());
        var anna = login(user);

        getWith("/api/notifications", anna).andExpect(status().isOk())
                .andExpect(jsonPath("$.types[?(@.key == 'othermodule.event')]").isEmpty());
        set(anna, TestChannels.TYPE, "recording", "{\"enabled\":true}").andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT enabled FROM core.notification_settings WHERE user_id = ? "
                + "AND event_type = 'othermodule.event'", Boolean.class, user.id())).isTrue();
    }

    @Test
    void needsASignIn() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
