package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A disabled module contributes nothing to the property finances, although its data is kept. */
@TestPropertySource(properties = "logis.modules.vacationrental.enabled=false")
class DisabledModuleFinancesTest extends VacationRentalTest {

    @Test
    void noStatementItems() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        var unit = unitIds(property).getFirst();
        var booking = jdbc.queryForObject("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults) "
                + "VALUES (?, DATE '2026-11-06', DATE '2026-11-08', 2) RETURNING id", Long.class, unit);
        settleDirectly(unit, booking);
        postJson("/api/properties/" + property + "/costs", Map.of("date", "2026-11-12", "amount", "320.00"),
                anna.session()).andExpect(status().isCreated());

        getWith("/api/properties/" + property + "/finances?year=2026", anna.session())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total.income").value(0.00));
    }
}
