package app.logis.core.ledger;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hilfen fuer Ledger-Tests: Benutzer mit Sitzung, Konten, Freigaben, Transaktionen.
 */
abstract class LedgerTest extends IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    record Actor(TestUser user, MockHttpSession session) {
        Long id() {
            return user.id();
        }
    }

    protected Actor actor() throws Exception {
        return actor(Role.USER);
    }

    protected Actor actor(Role role) throws Exception {
        var user = createUser(role, true);
        return new Actor(user, login(user));
    }

    protected Long personalEntity(Actor actor) {
        return jdbc.queryForObject("SELECT id FROM core.legal_entities WHERE manager_user_id = ? AND personal",
                Long.class, actor.id());
    }

    protected Long createEntity(Actor actor, String name) throws Exception {
        var body = postJson("/api/legal-entities", Map.of("name", name, "kind", "GBR"), actor.session())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected Long createAccount(Actor actor, Long entityId, String name, String openingBalance) throws Exception {
        var body = new HashMap<String, Object>(Map.of("legalEntityId", entityId, "name", name, "kind", "CHECKING"));
        if (openingBalance != null) {
            body.put("openingBalance", openingBalance);
        }
        var json = postJson("/api/accounts", body, actor.session())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected Long createAccount(Actor actor) throws Exception {
        return createAccount(actor, personalEntity(actor), "Konto " + System.nanoTime(), null);
    }

    protected void share(Actor owner, Long accountId, Actor target, String role) throws Exception {
        postJson("/api/shares/account/" + accountId, Map.of("userId", target.id(), "role", role), owner.session())
                .andExpect(status().isOk());
    }

    protected Long addTransaction(Actor actor, Long accountId, String amount, String date, Long categoryId)
            throws Exception {
        var body = new HashMap<String, Object>(Map.of("accountId", accountId, "amount", amount, "bookingDate", date));
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        var json = postJson("/api/transactions", body, actor.session())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected Long categoryId(String name) {
        return jdbc.queryForObject("SELECT id FROM core.categories WHERE name = ? ORDER BY id LIMIT 1", Long.class,
                name);
    }
}
