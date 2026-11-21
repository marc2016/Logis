package app.logis.core.preferences;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Settings per user (specs/user-preferences; design D1 of booking-compact-view). */
class PreferencesTest extends IntegrationTest {

    private ResultActions set(MockHttpSession session, String key, String jsonValue) throws Exception {
        return mvc.perform(put("/api/preferences/" + key).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(jsonValue));
    }

    @Test
    void readSetOverwriteAndDelete() throws Exception {
        var anna = login(createUser(Role.USER, true));
        getWith("/api/preferences", anna).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        set(anna, "vacationrental.bookingView", "\"compact\"").andExpect(status().isNoContent());
        set(anna, "core.tableColumns.vacationrental.bookings", "[\"agency\",\"nights\"]").andExpect(status().isNoContent());
        getWith("/api/preferences", anna)
                .andExpect(jsonPath("$['vacationrental.bookingView']").value("compact"))
                .andExpect(jsonPath("$['core.tableColumns.vacationrental.bookings'][1]").value("nights"));

        set(anna, "vacationrental.bookingView", "\"full\"").andExpect(status().isNoContent());
        getWith("/api/preferences", anna).andExpect(jsonPath("$['vacationrental.bookingView']").value("full"));

        mvc.perform(delete("/api/preferences/vacationrental.bookingView").with(csrf()).session(anna))
                .andExpect(status().isNoContent());
        getWith("/api/preferences", anna).andExpect(jsonPath("$['vacationrental.bookingView']").doesNotExist());
    }

    @Test
    void ownSettingsOnly() throws Exception {
        var anna = login(createUser(Role.USER, true));
        var bert = login(createUser(Role.USER, true));
        set(anna, "vacationrental.bookingView", "\"compact\"").andExpect(status().isNoContent());
        getWith("/api/preferences", bert).andExpect(jsonPath("$").isEmpty());
        set(bert, "vacationrental.bookingView", "\"full\"").andExpect(status().isNoContent());
        getWith("/api/preferences", anna).andExpect(jsonPath("$['vacationrental.bookingView']").value("compact"));
    }

    @Test
    void limits() throws Exception {
        var anna = login(createUser(Role.USER, true));
        var big = "\"" + "x".repeat(PreferencesController.MAX_VALUE_BYTES) + "\"";
        for (var bad : List.of(new String[] {"Core.view", "\"a\""}, new String[] {"view", "\"a\""},
                new String[] {"core.view", big}, new String[] {"core." + "x".repeat(PreferencesController.MAX_KEY), "1"})) {
            set(anna, bad[0], bad[1]).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(PreferencesController.INVALID));
        }
        for (int i = 0; i < PreferencesController.MAX_KEYS; i++) {
            set(anna, "core.setting" + i, String.valueOf(i)).andExpect(status().isNoContent());
        }
        set(anna, "core.oneMore", "1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(PreferencesController.INVALID));
        // Changing an existing setting at the limit still works.
        set(anna, "core.setting0", "42").andExpect(status().isNoContent());
        getWith("/api/preferences", anna).andExpect(jsonPath("$['core.setting0']").value(42));
    }

    @Test
    void signedOutIsRejected() throws Exception {
        mvc.perform(get("/api/preferences")).andExpect(status().isUnauthorized());
    }
}
