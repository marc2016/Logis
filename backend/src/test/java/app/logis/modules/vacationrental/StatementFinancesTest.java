package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatementFinancesTest extends VacationRentalTest {

    @Test
    void settledBookingAppearsOnThePropertyOnTheStatementDate() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        var unit = unitIds(property).getFirst();
        var body = stay(unit, "2026-11-06", "2026-11-08");
        body.put("externalRef", "275505");
        body.put("expectedRent", "248.58");
        var booking = id(book(anna, body));
        booking(anna, unit, "2026-11-20", "2026-11-22"); // planned: contributes nothing
        var statement = id(postJson("/api/vacationrental/statements", Map.of("unitId", unit, "provider", "Nord",
                "periodFrom", "2026-11-01", "periodTo", "2026-11-30", "statementDate", "2026-11-30"), anna.session()));
        putJson("/api/vacationrental/statements/" + statement + "/lines/" + booking,
                Map.of("commission", "37.29", "cleaning", "60.00"), anna.session()).andExpect(status().isOk());
        postJson("/api/properties/" + property + "/costs", Map.of("date", "2026-11-12", "amount", "320.00",
                "counterparty", "Handwerker Meyer"), anna.session()).andExpect(status().isCreated());

        getWith("/api/properties/" + property + "/finances?year=2026", anna.session())
                .andExpect(jsonPath("$.months[10].income").value(248.58))
                .andExpect(jsonPath("$.months[10].expenses").value(417.29))
                .andExpect(jsonPath("$.months[10].result").value(-168.71))
                .andExpect(jsonPath("$.items[*].label",
                        containsInAnyOrder("Buchung 275505", "Provision", "Reinigung", "Handwerker Meyer")))
                .andExpect(jsonPath("$.items[?(@.label == 'Provision')].date").value("2026-11-30"))
                .andExpect(jsonPath("$.items[?(@.label == 'Provision')].sourceType")
                        .value("vacationrental.statement"));
    }
}
