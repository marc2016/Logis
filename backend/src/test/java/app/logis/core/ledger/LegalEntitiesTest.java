package app.logis.core.ledger;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegalEntitiesTest extends LedgerTest {

    // --- 3.1 ------------------------------------------------------------------------------------

    @Test
    void newUserGetsExactlyOnePersonalEntity() throws Exception {
        var anna = actor();
        var rows = jdbc.queryForList("SELECT name, kind FROM core.legal_entities WHERE manager_user_id = ? AND personal",
                anna.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("kind", "PRIVATE_PERSON")
                .containsEntry("name", "Test " + anna.user().username() + " privat");
    }

    @Test
    void adminCreatedUserGetsPersonalEntity() throws Exception {
        var admin = actor(app.logis.core.users.Role.ADMIN);
        var name = "neu" + System.nanoTime();
        postJson("/api/admin/users", Map.of("username", name, "displayName", "Neu", "role", "USER",
                "password", "start-passwort-123"), admin.session()).andExpect(status().isCreated());
        var count = jdbc.queryForObject("SELECT count(*) FROM core.legal_entities le JOIN core.users u "
                + "ON u.id = le.manager_user_id WHERE u.username = ? AND le.personal AND le.name = 'Neu privat'",
                Integer.class, name);
        assertThat(count).isEqualTo(1);
    }

    // --- 3.2 ------------------------------------------------------------------------------------

    @Test
    void createListAndDuplicateName() throws Exception {
        var anna = actor();
        createEntity(anna, "GbR Ostsee");
        postJson("/api/legal-entities", Map.of("name", "gbr ostsee", "kind", "GBR"), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ein Rechtsträger mit diesem Namen existiert bereits"));
        getWith("/api/legal-entities", anna.session())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].personal").value(true))
                .andExpect(jsonPath("$[1].name").value("GbR Ostsee"));

        // The same name for another user is allowed.
        createEntity(actor(), "GbR Ostsee");
    }

    @Test
    void renameAndDeleteRules() throws Exception {
        var anna = actor();
        var gbr = createEntity(anna, "GbR Ostsee");
        mvc.perform(put("/api/legal-entities/" + gbr).with(csrf()).session(anna.session())
                        .contentType("application/json").content("{\"name\":\"Ostsee GbR\",\"kind\":\"GBR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ostsee GbR"));

        var accountId = createAccount(anna, gbr, "GbR-Giro", null);
        getWith("/api/accounts/" + accountId, anna.session())
                .andExpect(jsonPath("$.legalEntity.name").value("Ostsee GbR"));

        mvc.perform(delete("/api/legal-entities/" + gbr).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Rechtsträger hat noch Konten"));
        mvc.perform(delete("/api/legal-entities/" + personalEntity(anna)).with(csrf()).session(anna.session()))
                .andExpect(status().isBadRequest());

        var empty = createEntity(anna, "Leer");
        mvc.perform(delete("/api/legal-entities/" + empty).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
    }

    @Test
    void foreignEntityIsNotFound() throws Exception {
        var anna = actor();
        var bert = actor();
        var gbr = createEntity(anna, "GbR Ostsee");
        mvc.perform(put("/api/legal-entities/" + gbr).with(csrf()).session(bert.session())
                        .contentType("application/json").content("{\"name\":\"X\",\"kind\":\"GBR\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/legal-entities/" + gbr).with(csrf()).session(bert.session()))
                .andExpect(status().isNotFound());
        postJson("/api/accounts", Map.of("legalEntityId", gbr, "name", "X", "kind", "CHECKING"), bert.session())
                .andExpect(status().isNotFound());
    }
}
