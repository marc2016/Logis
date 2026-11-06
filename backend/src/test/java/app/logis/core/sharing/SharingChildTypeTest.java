package app.logis.core.sharing;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Child types with a test type "test.part" below "account": parts of account N have the IDs N*1000+1 and N*1000+2.
 */
@Import(SharingChildTypeTest.PartTypeConfig.class)
class SharingChildTypeTest extends IntegrationTest {

    static final String PART = "test.part";

    @TestConfiguration(proxyBeanMethods = false)
    static class PartTypeConfig {
        @Bean
        ShareableType testPartType() {
            return new ShareableType() {
                @Override
                public String type() {
                    return PART;
                }

                @Override
                public String parentType() {
                    return "account";
                }

                @Override
                public Optional<Long> parentOf(Long objectId) {
                    return objectId > 1000 ? Optional.of(objectId / 1000) : Optional.empty();
                }

                @Override
                public Set<Long> childrenOf(Long parentId) {
                    return Set.of(parentId * 1000 + 1, parentId * 1000 + 2);
                }

                @Override
                public Set<ShareRole> allowedRoles() {
                    return Set.of(ShareRole.VIEWER, ShareRole.EDITOR);
                }
            };
        }
    }

    @Autowired
    SharingService sharing;
    @Autowired
    JdbcTemplate jdbc;

    private Long account(TestUser owner) throws Exception {
        var entity = jdbc.queryForObject("SELECT id FROM core.legal_entities WHERE manager_user_id = ? AND personal",
                Long.class, owner.id());
        var body = postJson("/api/accounts", Map.of("legalEntityId", entity, "name", "Giro", "kind", "CHECKING"),
                login(owner)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void effectiveRoleIsMaximumOfParentAndChild() throws Exception {
        var owner = createUser(Role.USER, true);
        var dieter = createUser(Role.USER, true);
        var accountId = account(owner);
        var part1 = accountId * 1000 + 1;
        var part2 = accountId * 1000 + 2;

        sharing.invite("account", accountId, owner.id(), dieter.id(), ShareRole.VIEWER);
        sharing.invite(PART, part1, owner.id(), dieter.id(), ShareRole.EDITOR);

        assertThat(sharing.roleOf(PART, part1, dieter.id())).contains(ShareRole.EDITOR);
        assertThat(sharing.roleOf(PART, part2, dieter.id())).contains(ShareRole.VIEWER);
        assertThat(sharing.roleOf(PART, part1, owner.id())).contains(ShareRole.OWNER);
        assertThat(sharing.rolesFor(PART, dieter.id(), ShareRole.VIEWER))
                .containsEntry(part1, ShareRole.EDITOR).containsEntry(part2, ShareRole.VIEWER);
        assertThat(sharing.accessibleIds(PART, dieter.id(), ShareRole.EDITOR)).containsExactly(part1);
    }

    @Test
    void childRoleDoesNotSpread() throws Exception {
        var owner = createUser(Role.USER, true);
        var carla = createUser(Role.USER, true);
        var accountId = account(owner);
        var part1 = accountId * 1000 + 1;

        sharing.invite(PART, part1, owner.id(), carla.id(), ShareRole.EDITOR);

        assertThat(sharing.roleOf("account", accountId, carla.id())).isEmpty();
        assertThat(sharing.roleOf(PART, accountId * 1000 + 2, carla.id())).isEmpty();
        assertThat(sharing.accessibleIds(PART, carla.id(), ShareRole.VIEWER)).containsExactly(part1);
    }

    @Test
    void parentOwnersManageChildShares() throws Exception {
        var owner = createUser(Role.USER, true);
        var carla = createUser(Role.USER, true);
        var bert = createUser(Role.USER, true);
        var accountId = account(owner);
        var part1 = accountId * 1000 + 1;
        sharing.invite(PART, part1, owner.id(), carla.id(), ShareRole.EDITOR);

        assertThat(sharing.canManage(PART, part1, owner.id())).isTrue();
        assertThat(sharing.canManage(PART, part1, carla.id())).isFalse();

        var ownerSession = login(owner);
        getWith("/api/shares/account/" + accountId, ownerSession)
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.canManage").value(true));
        getWith("/api/shares/" + PART + "/" + part1, ownerSession)
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.canManage").value(true));

        var carlaSession = login(carla);
        postJson("/api/shares/" + PART + "/" + part1, Map.of("userId", bert.id(), "role", "VIEWER"), carlaSession)
                .andExpect(status().isForbidden());
        getWith("/api/shares/" + PART + "/" + part1, carlaSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("EDITOR"))
                .andExpect(jsonPath("$.canManage").value(false));

        // Bert has no access at all: the child object does not exist for him.
        postJson("/api/shares/" + PART + "/" + part1, Map.of("userId", carla.id(), "role", "VIEWER"), login(bert))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerRoleIsNotAllowedOnChildren() throws Exception {
        var owner = createUser(Role.USER, true);
        var carla = createUser(Role.USER, true);
        var accountId = account(owner);

        assertThatThrownBy(() -> sharing.invite(PART, accountId * 1000 + 1, owner.id(), carla.id(), ShareRole.OWNER))
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasMessage("Für Einheiten gibt es nur die Rollen Lesen und Bearbeiten");
    }

    @Test
    void revokingChildShareRemovesAccess() throws Exception {
        var owner = createUser(Role.USER, true);
        var carla = createUser(Role.USER, true);
        var accountId = account(owner);
        var part1 = accountId * 1000 + 1;
        sharing.invite(PART, part1, owner.id(), carla.id(), ShareRole.EDITOR);

        sharing.revoke(PART, part1, owner.id(), carla.id());

        assertThat(sharing.roleOf(PART, part1, carla.id())).isEmpty();
    }
}
