package app.logis.core.ledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransfersTest extends LedgerTest {

    record Setup(Actor anna, Long giro, Long savings, String transferId, Long outId) {
    }

    private Setup transfer() throws Exception {
        var anna = actor();
        var giro = createAccount(anna, personalEntity(anna), "Giro", "1000.00");
        var savings = createAccount(anna, personalEntity(anna), "Tagesgeld", "0.00");
        var json = postJson("/api/transfers", Map.of("fromAccountId", giro, "toAccountId", savings,
                "bookingDate", "2026-10-05", "amount", "500.00", "note", "Sparen"), anna.session())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String transferId = JsonPath.read(json, "$[0].transferId");
        java.util.List<Number> outIds = JsonPath.read(json, "$[?(@.amount == '-500.00')].id");
        Long outId = outIds.getFirst().longValue();
        return new Setup(anna, giro, savings, transferId, outId);
    }

    @Test
    void transferMovesBalancesButNotSums() throws Exception {
        var s = transfer();
        getWith("/api/accounts/" + s.giro(), s.anna().session()).andExpect(jsonPath("$.balance").value("500.00"));
        getWith("/api/accounts/" + s.savings(), s.anna().session()).andExpect(jsonPath("$.balance").value("500.00"));
        getWith("/api/transactions", s.anna().session())
                .andExpect(jsonPath("$.income").value("0.00"))
                .andExpect(jsonPath("$.expense").value("0.00"))
                .andExpect(jsonPath("$.items[?(@.accountId == " + s.giro() + ")].counterAccountName").value("Tagesgeld"));
    }

    @Test
    void updateChangesBothSides() throws Exception {
        var s = transfer();
        var body = new HashMap<String, Object>(Map.of("fromAccountId", s.giro(), "toAccountId", s.savings(),
                "bookingDate", "2026-10-06", "amount", "200.00", "version", 0));
        mvc.perform(put("/api/transfers/" + s.transferId()).with(csrf()).session(s.anna().session())
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        getWith("/api/accounts/" + s.giro(), s.anna().session()).andExpect(jsonPath("$.balance").value("800.00"));
        getWith("/api/accounts/" + s.savings(), s.anna().session()).andExpect(jsonPath("$.balance").value("200.00"));

        // Normale Bearbeitung einer Umbuchungsseite ist nicht erlaubt.
        mvc.perform(put("/api/transactions/" + s.outId()).with(csrf()).session(s.anna().session())
                        .contentType("application/json").content("{\"accountId\":" + s.giro()
                                + ",\"amount\":\"-1.00\",\"bookingDate\":\"2026-10-06\",\"version\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingOneSideDeletesBoth() throws Exception {
        var s = transfer();
        mvc.perform(delete("/api/transactions/" + s.outId()).with(csrf()).session(s.anna().session()))
                .andExpect(status().isNoContent());
        getWith("/api/transactions", s.anna().session()).andExpect(jsonPath("$.totalItems").value(0));
        getWith("/api/accounts/" + s.savings(), s.anna().session()).andExpect(jsonPath("$.balance").value("0.00"));
    }

    @Test
    void targetAccountNeedsEditor() throws Exception {
        var bert = actor();
        var anna = actor();
        var own = createAccount(bert);
        var annas = createAccount(anna);
        share(anna, annas, bert, "VIEWER");
        postJson("/api/transfers", Map.of("fromAccountId", own, "toAccountId", annas, "bookingDate", "2026-10-05",
                "amount", "10.00"), bert.session()).andExpect(status().isForbidden());
        postJson("/api/transfers", Map.of("fromAccountId", own, "toAccountId", own, "bookingDate", "2026-10-05",
                "amount", "10.00"), bert.session()).andExpect(status().isBadRequest());
    }
}
