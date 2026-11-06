package app.logis.core.realestate;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anna owns the account "GbR-Giro" and a property with "Whg. 1" and "Whg. 2". Carla is EDITOR of "Whg. 2" only,
 * Dieter is EDITOR of the account and VIEWER of the property.
 */
class TransactionTargetsTest extends RealestateTest {

    record World(Actor anna, Actor carla, Actor dieter, Long account, Long property, Long unit1, Long unit2) {
    }

    private World world() throws Exception {
        var anna = actor();
        var carla = actor();
        var dieter = actor();
        var ids = twoUnitProperty(anna);
        var account = account(anna, "GbR-Giro");
        share(anna, "unit", ids[2], carla, "EDITOR");
        share(anna, "property", ids[0], dieter, "VIEWER");
        share(anna, "account", account, dieter, "EDITOR");
        return new World(anna, carla, dieter, account, ids[0], ids[1], ids[2]);
    }

    private Map<String, Object> tx(World w, String amount, String counterparty, String note) {
        var body = new HashMap<String, Object>(Map.of("accountId", w.account(), "amount", amount,
                "bookingDate", "2026-10-03", "counterparty", counterparty));
        if (note != null) {
            body.put("note", note);
        }
        return body;
    }

    private Map<String, Object> withTarget(Map<String, Object> body, String key, Long id) {
        var copy = new HashMap<>(body);
        copy.put(key, id);
        return copy;
    }

    private int version(Long txId) {
        return jdbcVersion(txId);
    }

    private int jdbcVersion(Long txId) {
        return jdbc.queryForObject("SELECT version FROM core.transactions WHERE id = ?", Integer.class, txId);
    }

    // --- 4.1 Attribute ------------------------------------------------------------------------

    @Test
    void attributeOnCreateAndUpdate() throws Exception {
        var w = world();
        var created = postJson("/api/transactions", withTarget(tx(w, "95.00", "Gast Müller", null), "unitId", w.unit2()),
                w.anna().session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitId").value(w.unit2()))
                .andExpect(jsonPath("$.targetName").value(org.hamcrest.Matchers.endsWith(" · Whg. 2")))
                .andExpect(jsonPath("$.access").value("FULL"));
        var txId = id(created);

        var body = withTarget(tx(w, "95.00", "Gast Müller", null), "propertyId", w.property());
        body.put("version", version(txId));
        putJson("/api/transactions/" + txId, body, w.anna().session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(w.property()))
                .andExpect(jsonPath("$.unitId").doesNotExist());

        var both = withTarget(withTarget(tx(w, "95.00", "Gast Müller", null), "propertyId", w.property()), "unitId",
                w.unit1());
        both.put("version", version(txId));
        putJson("/api/transactions/" + txId, both, w.anna().session()).andExpect(status().isBadRequest());
    }

    @Test
    void attributingNeedsEditorOnTheTarget() throws Exception {
        var w = world();
        postJson("/api/transactions", withTarget(tx(w, "-40.00", "Reinigung", null), "unitId", w.unit1()),
                w.dieter().session())
                .andExpect(status().isForbidden());

        var txId = transaction(w.dieter(), tx(w, "-40.00", "Reinigung", null));
        var body = withTarget(tx(w, "-40.00", "Reinigung", null), "propertyId", w.property());
        body.put("version", version(txId));
        putJson("/api/transactions/" + txId, body, w.dieter().session()).andExpect(status().isForbidden());
    }

    @Test
    void transfersCannotBeAttributed() throws Exception {
        var w = world();
        var savings = account(w.anna(), "Tagesgeld");
        postJson("/api/transfers", Map.of("fromAccountId", w.account(), "toAccountId", savings, "amount", "500.00",
                "bookingDate", "2026-10-05"), w.anna().session()).andExpect(status().isCreated());
        var sideId = jdbc.queryForObject("SELECT id FROM core.transactions WHERE account_id = ? AND transfer_id IS NOT NULL",
                Long.class, w.account());

        var body = withTarget(tx(w, "-500.00", "x", null), "propertyId", w.property());
        body.put("version", version(sideId));
        putJson("/api/transactions/" + sideId, body, w.anna().session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Umbuchungen können keinem Objekt zugeordnet werden"));
    }

    @Test
    void archivedTargetIsKeptButNotSelectable() throws Exception {
        var w = world();
        var txId = transaction(w.anna(), withTarget(tx(w, "95.00", "Gast", null), "unitId", w.unit1()));
        postJson("/api/units/" + w.unit1() + "/archive", Map.of(), w.anna().session()).andExpect(status().isOk());

        var unchanged = withTarget(tx(w, "96.00", "Gast", null), "unitId", w.unit1());
        unchanged.put("version", version(txId));
        putJson("/api/transactions/" + txId, unchanged, w.anna().session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitId").value(w.unit1()));

        postJson("/api/transactions", withTarget(tx(w, "1.00", "Neu", null), "unitId", w.unit1()), w.anna().session())
                .andExpect(status().isBadRequest());
    }

    // --- 4.2 List -----------------------------------------------------------------------------

    @Test
    void carlaSeesOnlyHerUnitsTransactionsWithExactTotals() throws Exception {
        var w = world();
        transaction(w.anna(), withTarget(tx(w, "95.00", "Gast Müller", null), "unitId", w.unit2()));
        transaction(w.anna(), withTarget(tx(w, "-40.10", "Reinigung", null), "unitId", w.unit2()));
        transaction(w.anna(), withTarget(tx(w, "-310.00", "Grundsteuer", null), "propertyId", w.property()));
        transaction(w.anna(), withTarget(tx(w, "70.00", "Gast Meier", null), "unitId", w.unit1()));
        transaction(w.anna(), tx(w, "-12.00", "Bäcker", null));

        getWith("/api/transactions?unitId=" + w.unit2(), w.carla().session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.income").value("95.00"))
                .andExpect(jsonPath("$.expense").value("-40.10"));
        getWith("/api/transactions", w.carla().session())
                .andExpect(jsonPath("$.items[*].counterparty", containsInAnyOrder("Gast Müller", "Reinigung")));
        getWith("/api/transactions?propertyId=" + w.property(), w.carla().session())
                .andExpect(jsonPath("$.items[*].counterparty", containsInAnyOrder("Gast Müller", "Reinigung")));

        getWith("/api/transactions?propertyId=" + w.property(), w.anna().session())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.income").value("165.00"))
                .andExpect(jsonPath("$.expense").value("-350.10"));
        getWith("/api/transactions?accountId=" + w.account(), w.anna().session())
                .andExpect(jsonPath("$.items", hasSize(5)));
    }

    @Test
    void noteIsNotSearchedThroughTheTarget() throws Exception {
        var w = world();
        transaction(w.anna(), withTarget(tx(w, "95.00", "Gast", "geheimer Rabatt"), "unitId", w.unit2()));

        getWith("/api/transactions?q=geheim", w.carla().session()).andExpect(jsonPath("$.items", hasSize(0)));
        getWith("/api/transactions?q=gast", w.carla().session()).andExpect(jsonPath("$.items", hasSize(1)));
        getWith("/api/transactions?q=geheim", w.anna().session()).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void strangersSeeNothing() throws Exception {
        var w = world();
        var bert = actor();
        transaction(w.anna(), withTarget(tx(w, "95.00", "Gast", null), "unitId", w.unit2()));

        getWith("/api/transactions", bert.session()).andExpect(jsonPath("$.items", hasSize(0)));
        getWith("/api/transactions?unitId=" + w.unit2(), bert.session()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    // --- 4.3 Reduced rows ---------------------------------------------------------------------

    @Test
    void reducedRowsHideAccountDataAndAreReadOnly() throws Exception {
        var w = world();
        var txId = transaction(w.anna(), withTarget(tx(w, "95.00", "Gast Müller", "intern"), "unitId", w.unit2()));

        var json = getWith("/api/transactions?unitId=" + w.unit2(), w.carla().session())
                .andExpect(jsonPath("$.items[0].access").value("TARGET"))
                .andExpect(jsonPath("$.items[0].amount").value("95.00"))
                .andExpect(jsonPath("$.items[0].counterparty").value("Gast Müller"))
                .andExpect(jsonPath("$.items[0].unitId").value(w.unit2()))
                .andExpect(jsonPath("$.items[0].targetName").exists())
                .andExpect(jsonPath("$.items[0].accountId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].accountName").value(nullValue()))
                .andExpect(jsonPath("$.items[0].note").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("GbR-Giro").doesNotContain("intern");

        getWith("/api/transactions/" + txId, w.carla().session()).andExpect(status().is4xxClientError());
        var body = tx(w, "1.00", "x", null);
        body.put("version", version(txId));
        putJson("/api/transactions/" + txId, body, w.carla().session()).andExpect(status().isNotFound());
        mvc.perform(delete("/api/transactions/" + txId).with(csrf()).session(w.carla().session()))
                .andExpect(status().isNotFound());

        getWith("/api/transactions?unitId=" + w.unit2(), w.anna().session())
                .andExpect(jsonPath("$.items[0].access").value("FULL"))
                .andExpect(jsonPath("$.items[0].accountName").value("GbR-Giro"))
                .andExpect(jsonPath("$.items[0].note").value("intern"));
    }
}
