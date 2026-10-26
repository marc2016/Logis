package app.logis.core.ledger;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionListTest extends LedgerTest {

    @Test
    void sumsAreExactAcrossPagesAndExcludeTransfers() throws Exception {
        var anna = actor();
        var giro = createAccount(anna);
        var savings = createAccount(anna);
        for (int i = 1; i <= 7; i++) {
            addTransaction(anna, giro, "0.10", "2026-10-0" + i, null);
            addTransaction(anna, giro, "-0.20", "2026-10-0" + i, null);
        }
        addTransaction(anna, giro, "-99.99", "2026-09-30", null); // anderer Monat
        postJson("/api/transfers", Map.of("fromAccountId", giro, "toAccountId", savings, "bookingDate", "2026-10-05",
                "amount", "500.00"), anna.session()).andExpect(status().isCreated());

        getWith("/api/transactions?from=2026-10-01&to=2026-10-31&size=5&page=1", anna.session())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.totalItems").value(16))
                .andExpect(jsonPath("$.income").value("0.70"))
                .andExpect(jsonPath("$.expense").value("-1.40"));
    }

    @Test
    void onlySharedAccountsAppear() throws Exception {
        var anna = actor();
        var bert = actor();
        var annas = createAccount(anna);
        var shared = createAccount(anna);
        addTransaction(anna, annas, "-1.00", "2026-10-01", null);
        addTransaction(anna, shared, "-2.00", "2026-10-01", null);
        share(anna, shared, bert, "VIEWER");

        getWith("/api/transactions", bert.session())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].accountId").value(shared));
        getWith("/api/transactions?accountId=" + annas, bert.session()).andExpect(status().isNotFound());
    }

    @Test
    void categoryFilterIncludesChildrenAndUncategorizedExcludesTransfers() throws Exception {
        var anna = actor();
        var acc = createAccount(anna);
        var other = createAccount(anna);
        addTransaction(anna, acc, "-10.00", "2026-10-01", categoryId("Wohnen"));
        addTransaction(anna, acc, "-20.00", "2026-10-02", categoryId("Grundsteuer"));
        addTransaction(anna, acc, "-30.00", "2026-10-03", categoryId("Lebensmittel"));
        addTransaction(anna, acc, "-40.00", "2026-10-04", null);
        postJson("/api/transfers", Map.of("fromAccountId", acc, "toAccountId", other, "bookingDate", "2026-10-05",
                "amount", "5.00"), anna.session());

        getWith("/api/transactions?accountId=" + acc + "&categoryId=" + categoryId("Wohnen"), anna.session())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.expense").value("-30.00"));
        getWith("/api/transactions?accountId=" + acc + "&uncategorized=true", anna.session())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].amount").value("-40.00"));
    }

    @Test
    void sortedByDateDescendingAndTextSearch() throws Exception {
        var anna = actor();
        var acc = createAccount(anna);
        addTransaction(anna, acc, "-1.00", "2026-10-01", null);
        addTransaction(anna, acc, "-3.00", "2026-10-03", null);
        addTransaction(anna, acc, "-2.00", "2026-10-02", null);
        postJson("/api/transactions", Map.of("accountId", acc, "amount", "-4.00", "bookingDate", "2026-09-01",
                "purpose", "Strom 100%_Ökostrom"), anna.session());

        getWith("/api/transactions?accountId=" + acc, anna.session())
                .andExpect(jsonPath("$.items[0].bookingDate").value("2026-10-03"))
                .andExpect(jsonPath("$.items[1].bookingDate").value("2026-10-02"))
                .andExpect(jsonPath("$.items[2].bookingDate").value("2026-10-01"));
        getWith("/api/transactions?accountId=" + acc + "&q=100%_ÖKO", anna.session())
                .andExpect(jsonPath("$.totalItems").value(1));
        getWith("/api/transactions?accountId=" + acc + "&size=500", anna.session())
                .andExpect(jsonPath("$.size").value(100));
    }
}
