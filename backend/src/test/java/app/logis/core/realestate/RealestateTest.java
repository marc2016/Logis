package app.logis.core.realestate;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers for real estate tests: users with session, properties, units, shares, accounts and transactions.
 */
abstract class RealestateTest extends IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    record Actor(TestUser user, MockHttpSession session) {
        Long id() {
            return user.id();
        }
    }

    protected Actor actor() throws Exception {
        var user = createUser(Role.USER, true);
        return new Actor(user, login(user));
    }

    protected Long personalEntity(Actor actor) {
        return jdbc.queryForObject("SELECT id FROM core.legal_entities WHERE manager_user_id = ? AND personal",
                Long.class, actor.id());
    }

    protected ResultActions createProperty(Actor actor, Map<String, Object> body) throws Exception {
        var request = new HashMap<String, Object>(Map.of("legalEntityId", personalEntity(actor)));
        request.putAll(body);
        return postJson("/api/properties", request, actor.session());
    }

    protected Long singleUnitProperty(Actor actor, String name) throws Exception {
        return id(createProperty(actor, Map.of("name", name, "multiUnit", false,
                "firstUnit", Map.of("usage", "VACATION_RENTAL"))).andExpect(status().isCreated()));
    }

    /** A property with the units "Whg. 1" and "Whg. 2"; returns property ID and both unit IDs. */
    protected long[] twoUnitProperty(Actor actor) throws Exception {
        var propertyId = id(createProperty(actor, Map.of("name", "Haus " + System.nanoTime(), "multiUnit", true,
                "firstUnit", Map.of("name", "Whg. 1", "usage", "VACATION_RENTAL"))).andExpect(status().isCreated()));
        postJson("/api/properties/" + propertyId + "/units", Map.of("name", "Whg. 2", "usage", "VACATION_RENTAL"),
                actor.session()).andExpect(status().isCreated());
        return new long[] {propertyId, unitId(propertyId, "Whg. 1"), unitId(propertyId, "Whg. 2")};
    }

    protected Long unitId(Long propertyId, String name) {
        return jdbc.queryForObject("SELECT id FROM core.units WHERE property_id = ? AND name = ?", Long.class,
                propertyId, name);
    }

    protected List<Long> unitIds(Long propertyId) {
        return jdbc.queryForList("SELECT id FROM core.units WHERE property_id = ? ORDER BY id", Long.class, propertyId);
    }

    protected void share(Actor owner, String type, Long objectId, Actor target, String role) throws Exception {
        postJson("/api/shares/" + type + "/" + objectId, Map.of("userId", target.id(), "role", role), owner.session())
                .andExpect(status().isOk());
    }

    protected Long account(Actor actor, String name) throws Exception {
        return id(postJson("/api/accounts", Map.of("legalEntityId", personalEntity(actor), "name", name,
                "kind", "CHECKING"), actor.session()).andExpect(status().isCreated()));
    }

    protected Long transaction(Actor actor, Map<String, Object> body) throws Exception {
        return id(postJson("/api/transactions", body, actor.session()).andExpect(status().isCreated()));
    }

    protected ResultActions putJson(String url, Object body, MockHttpSession session) throws Exception {
        return mvc.perform(put(url).with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected static Long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
