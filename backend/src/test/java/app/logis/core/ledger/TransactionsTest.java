package app.logis.core.ledger;

import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionsTest extends LedgerTest {

    private org.springframework.test.web.servlet.ResultActions putTx(Actor actor, Long id, Map<String, Object> body)
            throws Exception {
        return mvc.perform(put("/api/transactions/" + id).with(csrf()).session(actor.session())
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private Map<String, Object> body(Long accountId, String amount, Long categoryId, Integer version) {
        var b = new HashMap<String, Object>(Map.of("accountId", accountId, "amount", amount, "bookingDate", "2026-10-03",
                "counterparty", "Edeka"));
        if (categoryId != null) {
            b.put("categoryId", categoryId);
        }
        if (version != null) {
            b.put("version", version);
        }
        return b;
    }

    @Test
    void createChangesBalanceAndReturnsStringAmount() throws Exception {
        var anna = actor();
        var acc = createAccount(anna);
        postJson("/api/transactions", body(acc, "-84.90", categoryId("Lebensmittel"), null), anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("-84.90"))
                .andExpect(jsonPath("$.categoryName").value("Lebensmittel"))
                .andExpect(jsonPath("$.counterparty").value("Edeka"));
        getWith("/api/accounts/" + acc, anna.session()).andExpect(jsonPath("$.balance").value("-84.90"));
    }

    @Test
    void invalidAmounts() throws Exception {
        var anna = actor();
        var acc = createAccount(anna);
        for (var amount : new String[] {"0.00", "12.345"}) {
            postJson("/api/transactions", body(acc, amount, null, null), anna.session())
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rightsOnTransactions() throws Exception {
        var anna = actor();
        var bert = actor();
        var acc = createAccount(anna);
        postJson("/api/transactions", body(acc, "-1.00", null, null), bert.session()).andExpect(status().isNotFound());
        share(anna, acc, bert, "VIEWER");
        postJson("/api/transactions", body(acc, "-1.00", null, null), bert.session()).andExpect(status().isForbidden());
        var tx = addTransaction(anna, acc, "-1.00", "2026-10-01", null);
        mvc.perform(delete("/api/transactions/" + tx).with(csrf()).session(bert.session()))
                .andExpect(status().isForbidden());

        share(anna, acc, bert, "EDITOR");
        putTx(bert, tx, body(acc, "-2.00", categoryId("Gebühren"), 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryName").value("Gebühren"));

        // Admin without a share sees nothing.
        var admin = actor(Role.ADMIN);
        mvc.perform(delete("/api/transactions/" + tx).with(csrf()).session(admin.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveToOtherAccountNeedsEditorThere() throws Exception {
        var anna = actor();
        var bert = actor();
        var own = createAccount(anna);
        var foreign = createAccount(bert);
        var tx = addTransaction(anna, own, "-1.00", "2026-10-01", null);
        putTx(anna, tx, body(foreign, "-1.00", null, 0)).andExpect(status().isNotFound());
        share(bert, foreign, anna, "VIEWER");
        putTx(anna, tx, body(foreign, "-1.00", null, 0)).andExpect(status().isForbidden());
        share(bert, foreign, anna, "EDITOR");
        putTx(anna, tx, body(foreign, "-1.00", null, 0)).andExpect(jsonPath("$.accountId").value(foreign));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        var anna = actor();
        var acc = createAccount(anna);
        var tx = addTransaction(anna, acc, "-1.00", "2026-10-01", null);
        putTx(anna, tx, body(acc, "-2.00", null, 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        putTx(anna, tx, body(acc, "-3.00", null, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Transaktion wurde inzwischen geändert"));
    }

    @Test
    void archivedCategoryRules() throws Exception {
        var admin = actor(Role.ADMIN);
        var json = postJson("/api/admin/categories", Map.of("name", "Bald weg " + System.nanoTime(), "kind", "EXPENSE"),
                admin.session()).andReturn().getResponse().getContentAsString();
        Long cat = ((Number) JsonPath.read(json, "$.id")).longValue();
        var acc = createAccount(admin);
        var tx = addTransaction(admin, acc, "-1.00", "2026-10-01", cat);
        mvc.perform(post("/api/admin/categories/" + cat + "/archive").with(csrf()).session(admin.session()));

        postJson("/api/transactions", body(acc, "-1.00", cat, null), admin.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Kategorie ist nicht verfügbar"));
        // Existing assignment stays, even when editing other fields.
        putTx(admin, tx, body(acc, "-5.00", cat, 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(cat));
    }
}
