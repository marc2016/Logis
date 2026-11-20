package app.logis.core.dashboard;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Per-user start page tiles (specs/dashboard; design D1 of configurable-dashboard). */
class DashboardTest extends IntegrationTest {

    private ResultActions save(MockHttpSession session, Object tiles) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("tiles", tiles);
        return mvc.perform(put("/api/dashboard").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> tile(String id, String type, Map<String, Object> settings) {
        return Map.of("id", id, "type", type, "settings", settings);
    }

    @Test
    void notCustomisedSaveReadAndReset() throws Exception {
        var anna = login(createUser(Role.USER, true));
        getWith("/api/dashboard", anna).andExpect(status().isOk()).andExpect(jsonPath("$.tiles").doesNotExist());

        var tiles = List.of(tile("a", "core.balances", Map.of()),
                tile("b", "vacationrental.occupancy", Map.of("unitId", 7, "previous", true)),
                // A tile of a module this test does not know: kept as it is.
                tile("c", "othermodule.something-new", Map.of("x", "y")));
        save(anna, tiles).andExpect(status().isOk());
        getWith("/api/dashboard", anna)
                .andExpect(jsonPath("$.tiles.length()").value(3))
                .andExpect(jsonPath("$.tiles[1].type").value("vacationrental.occupancy"))
                .andExpect(jsonPath("$.tiles[1].settings.unitId").value(7))
                .andExpect(jsonPath("$.tiles[2].type").value("othermodule.something-new"));

        save(anna, List.of(tile("b", "core.todo", Map.of()))).andExpect(status().isOk());
        getWith("/api/dashboard", anna).andExpect(jsonPath("$.tiles.length()").value(1));

        save(anna, null).andExpect(status().isOk());
        getWith("/api/dashboard", anna).andExpect(jsonPath("$.tiles").doesNotExist());
    }

    @Test
    void eachUserHasTheirOwn() throws Exception {
        var anna = login(createUser(Role.USER, true));
        var bert = login(createUser(Role.USER, true));
        save(anna, List.of(tile("a", "core.todo", Map.of()))).andExpect(status().isOk());
        getWith("/api/dashboard", bert).andExpect(jsonPath("$.tiles").doesNotExist());
        save(bert, List.of(tile("x", "core.balances", Map.of()))).andExpect(status().isOk());
        getWith("/api/dashboard", anna).andExpect(jsonPath("$.tiles[0].type").value("core.todo"));
    }

    @Test
    void invalidTilesAreRejected() throws Exception {
        var anna = login(createUser(Role.USER, true));
        var many = new ArrayList<Map<String, Object>>();
        for (int i = 0; i <= DashboardController.MAX_TILES; i++) {
            many.add(tile("t" + i, "core.todo", Map.of()));
        }
        var big = Map.<String, Object>of("text", "x".repeat(DashboardController.MAX_SETTINGS_BYTES));
        List<Object> invalid = List.of(
                many,
                List.of(tile("a", "Core.Todo", Map.of())),
                List.of(tile("a", "core", Map.of())),
                List.of(tile("a", "core.todo", Map.of()), tile("a", "core.balances", Map.of())),
                List.of(tile("a", "core.todo", big)),
                List.of(tile("x".repeat(DashboardController.MAX_ID + 1), "core.todo", Map.of())),
                List.of(Map.of("id", "a", "type", "core.todo", "settings", List.of())),
                List.of(Map.of("id", "a", "type", "core.todo")),
                Map.of("id", "a"));
        for (var tiles : invalid) {
            save(anna, tiles).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(DashboardController.INVALID));
        }
        getWith("/api/dashboard", anna).andExpect(jsonPath("$.tiles").doesNotExist());
    }

    @Test
    void signedOutIsRejected() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }
}
