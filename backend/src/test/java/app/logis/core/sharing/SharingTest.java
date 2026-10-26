package app.logis.core.sharing;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Freigaben am Beispiel Konto (registrierter Typ "account").
 */
class SharingTest extends IntegrationTest {

    @Autowired
    SharingService sharing;

    record Ctx(TestUser owner, MockHttpSession ownerSession, TestUser other, MockHttpSession otherSession,
            Long accountId) {
    }

    private Ctx setup() throws Exception {
        var owner = createUser(Role.USER, true);
        var ownerSession = login(owner);
        var other = createUser(Role.USER, true);
        var otherSession = login(other);
        var entity = jdbcEntity(owner.id());
        var json = postJson("/api/accounts", Map.of("legalEntityId", entity, "name", "Giro", "kind", "CHECKING"),
                ownerSession).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long accountId = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
        return new Ctx(owner, ownerSession, other, otherSession, accountId);
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long jdbcEntity(Long userId) {
        return jdbc.queryForObject("SELECT id FROM core.legal_entities WHERE manager_user_id = ? AND personal",
                Long.class, userId);
    }

    private String url(Ctx c) {
        return "/api/shares/account/" + c.accountId();
    }

    // --- 2.1 Service ----------------------------------------------------------------------------

    @Test
    void roleHierarchyAndErrors() throws Exception {
        var c = setup();
        assertThat(ShareRole.OWNER.atLeast(ShareRole.EDITOR)).isTrue();
        assertThat(ShareRole.EDITOR.atLeast(ShareRole.VIEWER)).isTrue();
        assertThat(ShareRole.VIEWER.atLeast(ShareRole.EDITOR)).isFalse();

        assertThatThrownBy(() -> sharing.requireRole("account", c.accountId(), c.other().id(), ShareRole.VIEWER))
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
        sharing.invite("account", c.accountId(), c.owner().id(), c.other().id(), ShareRole.VIEWER);
        assertThat(sharing.requireRole("account", c.accountId(), c.other().id(), ShareRole.VIEWER))
                .isEqualTo(ShareRole.VIEWER);
        assertThatThrownBy(() -> sharing.requireRole("account", c.accountId(), c.other().id(), ShareRole.EDITOR))
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(sharing.accessibleIds("account", c.other().id(), ShareRole.VIEWER)).contains(c.accountId());
        assertThat(sharing.accessibleIds("account", c.other().id(), ShareRole.EDITOR)).doesNotContain(c.accountId());
    }

    // --- 2.2 API --------------------------------------------------------------------------------

    @Test
    void inviteIsImmediateAndRoleChangeNotDuplicate() throws Exception {
        var c = setup();
        getWith("/api/accounts/" + c.accountId(), c.otherSession()).andExpect(status().isNotFound());

        postJson(url(c), Map.of("userId", c.other().id(), "role", "VIEWER"), c.ownerSession())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares", hasSize(2)));
        getWith("/api/accounts/" + c.accountId(), c.otherSession()).andExpect(status().isOk());

        postJson(url(c), Map.of("userId", c.other().id(), "role", "EDITOR"), c.ownerSession())
                .andExpect(jsonPath("$.shares", hasSize(2)))
                .andExpect(jsonPath("$.shares[?(@.userId == " + c.other().id() + ")].role").value("EDITOR"));
    }

    @Test
    void inviteUnknownOrDisabledUser() throws Exception {
        var c = setup();
        var disabled = createUser(Role.USER, true);
        var entity = users.findById(disabled.id()).orElseThrow();
        entity.setEnabled(false);
        users.save(entity);
        for (var id : new Long[] {disabled.id(), 999_999L}) {
            postJson(url(c), Map.of("userId", id, "role", "VIEWER"), c.ownerSession())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Benutzer nicht gefunden"));
        }
    }

    @Test
    void editorCannotManageShares() throws Exception {
        var c = setup();
        var third = createUser(Role.USER, true);
        postJson(url(c), Map.of("userId", c.other().id(), "role", "EDITOR"), c.ownerSession());
        postJson(url(c), Map.of("userId", third.id(), "role", "VIEWER"), c.otherSession())
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerSeesListWithOwnRole() throws Exception {
        var c = setup();
        postJson(url(c), Map.of("userId", c.other().id(), "role", "VIEWER"), c.ownerSession());
        getWith(url(c), c.otherSession())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("VIEWER"))
                .andExpect(jsonPath("$.shares[0].username").exists())
                .andExpect(jsonPath("$.shares[0].displayName").exists());
    }

    @Test
    void revokeAndLeave() throws Exception {
        var c = setup();
        postJson(url(c), Map.of("userId", c.other().id(), "role", "VIEWER"), c.ownerSession());
        mvc.perform(delete(url(c) + "/" + c.other().id()).with(csrf()).session(c.ownerSession()))
                .andExpect(status().isNoContent());
        getWith("/api/accounts/" + c.accountId(), c.otherSession()).andExpect(status().isNotFound());

        postJson(url(c), Map.of("userId", c.other().id(), "role", "VIEWER"), c.ownerSession());
        mvc.perform(delete(url(c) + "/" + c.other().id()).with(csrf()).session(c.otherSession()))
                .andExpect(status().isNoContent());
        getWith("/api/accounts/" + c.accountId(), c.otherSession()).andExpect(status().isNotFound());
    }

    @Test
    void lastOwnerIsProtected() throws Exception {
        var c = setup();
        mvc.perform(patch(url(c) + "/" + c.owner().id()).with(csrf()).session(c.ownerSession())
                        .contentType("application/json").content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Es muss mindestens einen Owner geben"));
        mvc.perform(delete(url(c) + "/" + c.owner().id()).with(csrf()).session(c.ownerSession()))
                .andExpect(status().isConflict());

        // Mit zweitem Owner darf der erste gehen.
        postJson(url(c), Map.of("userId", c.other().id(), "role", "OWNER"), c.ownerSession());
        mvc.perform(delete(url(c) + "/" + c.owner().id()).with(csrf()).session(c.ownerSession()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownTypeGives404() throws Exception {
        var c = setup();
        getWith("/api/shares/unbekannt/" + c.accountId(), c.ownerSession()).andExpect(status().isNotFound());
    }

    // --- 2.3 Benutzersuche ----------------------------------------------------------------------

    @Test
    void userSearch() throws Exception {
        var c = setup();
        var prefix = c.other().username().substring(0, 6);
        getWith("/api/users/search?q=" + c.other().username(), c.ownerSession())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(c.other().id()))
                .andExpect(jsonPath("$[0].username").value(c.other().username()))
                .andExpect(jsonPath("$[0].displayName").exists())
                .andExpect(jsonPath("$[0].role").doesNotExist())
                .andExpect(jsonPath("$[0].enabled").doesNotExist());
        getWith("/api/users/search?q=u", c.ownerSession()).andExpect(jsonPath("$", hasSize(0)));
        getWith("/api/users/search?q=" + prefix.substring(0, 4), c.ownerSession())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(10)));

        var entity = users.findById(c.other().id()).orElseThrow();
        entity.setEnabled(false);
        users.save(entity);
        getWith("/api/users/search?q=" + c.other().username(), c.ownerSession()).andExpect(jsonPath("$", hasSize(0)));
    }
}
