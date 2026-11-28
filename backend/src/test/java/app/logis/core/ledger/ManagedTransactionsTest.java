package app.logis.core.ledger;

import app.logis.core.web.ApiException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Transactions managed by a module (spec transactions, „Transactions managed by a module“). Invented values only. */
class ManagedTransactionsTest extends LedgerTest {

    @Autowired
    ManagedTransactions managed;

    private Long unit(Actor actor) throws Exception {
        var json = postJson("/api/properties", Map.of("legalEntityId", personalEntity(actor), "name",
                "Haus " + System.nanoTime(), "multiUnit", false, "firstUnit", Map.of("usage", "LONG_TERM_RENTAL")),
                actor.session()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var propertyId = ((Number) JsonPath.read(json, "$.id")).longValue();
        return jdbc.queryForObject("SELECT id FROM core.units WHERE property_id = ?", Long.class, propertyId);
    }

    private ManagedTransactions.Input rent(Long account, Long unit, String amount) {
        return new ManagedTransactions.Input(account, LocalDate.of(2027, 4, 3), new BigDecimal(amount),
                ManagedTransactions.RENT_CATEGORY, "Erika Musterfrau", "Miete Whg. 1", unit);
    }

    @Test
    void createdWithCategoryTargetAndSource() throws Exception {
        var anna = actor();
        var account = createAccount(anna);
        var unit = unit(anna);
        var id = managed.create(anna.id(), "longtermrental.payment", 41L, rent(account, unit, "800.00"));

        getWith("/api/transactions?accountId=" + account, anna.session())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].amount").value("800.00"))
                .andExpect(jsonPath("$.items[0].categoryName").value("Mieteinnahmen"))
                .andExpect(jsonPath("$.items[0].counterparty").value("Erika Musterfrau"))
                .andExpect(jsonPath("$.items[0].unitId").value(unit))
                .andExpect(jsonPath("$.items[0].source.type").value("longtermrental.payment"))
                .andExpect(jsonPath("$.items[0].source.id").value(41));
        getWith("/api/accounts/" + account, anna.session()).andExpect(jsonPath("$.balance").value("800.00"));

        managed.update(anna.id(), id, rent(account, unit, "750.00"));
        assertThat(jdbc.queryForObject("SELECT amount FROM core.transactions WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("750.00");
        assertThat(managed.accountNames(List.of(id))).containsKey(id);

        managed.delete(anna.id(), id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.transactions WHERE id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void accountWithoutEditRights() throws Exception {
        var anna = actor();
        var bert = actor();
        var account = createAccount(anna);
        share(anna, account, bert, "VIEWER");
        var unit = unit(anna);
        assertThatThrownBy(() -> managed.create(bert.id(), "longtermrental.payment", 1L, rent(account, unit, "1.00")))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.transactions WHERE account_id = ?", Integer.class,
                account)).isZero();
    }

    @Test
    void archivedCategoryIsLeftOut() throws Exception {
        var anna = actor();
        var account = createAccount(anna);
        var unit = unit(anna);
        var input = new ManagedTransactions.Input(account, LocalDate.of(2027, 4, 3), new BigDecimal("10.00"),
                "Keine solche Kategorie", null, null, unit);
        var id = managed.create(anna.id(), "longtermrental.payment", 2L, input);
        assertThat(jdbc.queryForObject("SELECT category_id FROM core.transactions WHERE id = ?", Long.class, id))
                .isNull();
    }

    @Test
    void financesDoNotChangeManagedTransactions() throws Exception {
        var anna = actor();
        var account = createAccount(anna);
        var unit = unit(anna);
        var id = managed.create(anna.id(), "longtermrental.payment", 3L, rent(account, unit, "800.00"));
        var change = new HashMap<String, Object>(Map.of("accountId", account, "amount", "1.00", "bookingDate",
                "2027-04-03", "version", 0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/transactions/" + id)
                        .with(csrf()).session(anna.session()).contentType("application/json")
                        .content(json.writeValueAsString(change)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Transaktion gehört zu einer Mietzahlung und wird dort geändert"));
        mvc.perform(delete("/api/transactions/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict());
        // Own transactions stay as they were.
        var own = addTransaction(anna, account, "5.00", "2027-04-04", null);
        mvc.perform(delete("/api/transactions/" + own).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith("/api/transactions?accountId=" + account, anna.session())
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].source.type").value("longtermrental.payment"));
    }

    @Test
    void existingTransactionsHaveNoSource() throws Exception {
        var anna = actor();
        var own = addTransaction(anna, createAccount(anna), "5.00", "2027-04-04", null);
        assertThat(jdbc.queryForMap("SELECT source_type, source_id FROM core.transactions WHERE id = ?", own))
                .containsEntry("source_type", null).containsEntry("source_id", null);
    }
}
