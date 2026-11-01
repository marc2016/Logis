package app.logis.core.ledger;

import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountsTest extends LedgerTest {

    private String update(String name, boolean archived) {
        return "{\"name\":\"" + name + "\",\"kind\":\"CHECKING\",\"openingBalance\":\"100.00\",\"archived\":" + archived + "}";
    }

    @Test
    void createWithOpeningBalanceAndExactBalance() throws Exception {
        var anna = actor();
        var id = createAccount(anna, personalEntity(anna), "Girokonto", "100.00");
        addTransaction(anna, id, "0.10", "2026-10-01", null);
        addTransaction(anna, id, "0.20", "2026-10-02", null);
        addTransaction(anna, id, "-50.00", "2026-10-03", null);
        getWith("/api/accounts/" + id, anna.session())
                .andExpect(jsonPath("$.balance").value("50.30"))
                .andExpect(jsonPath("$.openingBalance").value("100.00"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.myRole").value("OWNER"));
    }

    @Test
    void invalidIbanIsRejected() throws Exception {
        var anna = actor();
        postJson("/api/accounts", Map.of("legalEntityId", personalEntity(anna), "name", "X", "kind", "CHECKING",
                "iban", "DE88370400440532013000"), anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die IBAN ist ungültig"));
    }

    @Test
    void editorCannotEditAccountAndForeignIsNotFound() throws Exception {
        var anna = actor();
        var bert = actor();
        var admin = actor(Role.ADMIN);
        var id = createAccount(anna);
        mvc.perform(put("/api/accounts/" + id).with(csrf()).session(bert.session())
                .contentType("application/json").content(update("X", false))).andExpect(status().isNotFound());
        getWith("/api/accounts/" + id, admin.session()).andExpect(status().isNotFound());
        getWith("/api/accounts", admin.session())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

        share(anna, id, bert, "EDITOR");
        mvc.perform(put("/api/accounts/" + id).with(csrf()).session(bert.session())
                .contentType("application/json").content(update("X", false))).andExpect(status().isForbidden());
    }

    @Test
    void mixedListWithForeignLegalEntity() throws Exception {
        var anna = actor();
        var bert = actor();
        var gbr = createEntity(anna, "GbR Ostsee");
        var gbrGiro = createAccount(anna, gbr, "GbR-Giro", null);
        createAccount(anna, gbr, "GbR-Kasse", null);
        var bertsOwn = createAccount(bert, personalEntity(bert), "Bert Giro", null);
        share(anna, gbrGiro, bert, "VIEWER");

        getWith("/api/accounts", bert.session())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + gbrGiro + ")].legalEntity.name").value("GbR Ostsee"))
                .andExpect(jsonPath("$[?(@.id == " + gbrGiro + ")].myRole").value("VIEWER"))
                .andExpect(jsonPath("$[?(@.id == " + gbrGiro + ")].legalEntity.taxNumber").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + bertsOwn + ")].myRole").value("OWNER"));
        // Bert does not see the GbR in their list of legal entities.
        getWith("/api/legal-entities", bert.session()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void archiveBlocksNewTransactionsAndRestore() throws Exception {
        var anna = actor();
        var id = createAccount(anna);
        addTransaction(anna, id, "-5.00", "2026-10-01", null);
        mvc.perform(put("/api/accounts/" + id).with(csrf()).session(anna.session())
                .contentType("application/json").content(update("Alt", true))).andExpect(status().isOk());
        postJson("/api/transactions", Map.of("accountId", id, "amount", "-1.00", "bookingDate", "2026-10-02"),
                anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Konto ist archiviert"));
        getWith("/api/transactions?accountId=" + id, anna.session()).andExpect(jsonPath("$.totalItems").value(1));
        getWith("/api/accounts", anna.session()).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        getWith("/api/accounts?includeArchived=true", anna.session())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].archived").value(true));
    }

    @Test
    void deleteOnlyWithoutTransactionsAndRemovesShares() throws Exception {
        var anna = actor();
        var bert = actor();
        var used = createAccount(anna);
        addTransaction(anna, used, "1.00", "2026-10-01", null);
        mvc.perform(delete("/api/accounts/" + used).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict());

        var empty = createAccount(anna);
        share(anna, empty, bert, "VIEWER");
        mvc.perform(delete("/api/accounts/" + empty).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        var orphans = jdbc.queryForObject("SELECT count(*) FROM core.object_shares s WHERE s.object_type = 'account' "
                + "AND NOT EXISTS (SELECT 1 FROM core.accounts a WHERE a.id = s.object_id)", Integer.class);
        assertThat(orphans).isZero();
    }
}
