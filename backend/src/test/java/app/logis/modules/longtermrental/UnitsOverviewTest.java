package app.logis.modules.longtermrental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnitsOverviewTest extends LongTermRentalTest {

    private static final String UNITS = "/api/longtermrental/units";

    @Test
    void tenantOrVacancyPerUnit() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        postJson("/api/properties/" + house[0] + "/units", Map.of("name", "Whg. 3", "usage", "LONG_TERM_RENTAL"),
                anna.session()).andExpect(status().isCreated());
        // Other usages do not belong to the module.
        postJson("/api/properties/" + house[0] + "/units", Map.of("name", "Whg. 4", "usage", "VACATION_RENTAL"),
                anna.session()).andExpect(status().isCreated());
        singleUnitProperty(anna, "Haus Waldeck");
        var end = today().plusDays(1);
        var running = tenancy(anna, house[1], today().minusDays(90), end);
        tenancy(anna, house[2], today().minusDays(90), today().minusDays(31));
        var last = today().minusDays(10);
        tenancy(anna, house[2], today().minusDays(30), last);
        var next = tenancy(anna, house[2], today().plusDays(20), null);

        getWith(UNITS, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].unit.name").value("Whg. 1"))
                .andExpect(jsonPath("$[0].canEdit").value(true))
                .andExpect(jsonPath("$[0].tenancy.id").value(running))
                .andExpect(jsonPath("$[0].tenancy.state").value("RUNNING"))
                .andExpect(jsonPath("$[0].tenancy.tenants[0].name").value("Erika Musterfrau"))
                .andExpect(jsonPath("$[0].tenancy.currentRent.total").value("800.00"))
                .andExpect(jsonPath("$[0].vacantSince").doesNotExist())
                // Vacant since the day after the last end; the future tenancy does not end the vacancy.
                .andExpect(jsonPath("$[1].unit.name").value("Whg. 2"))
                .andExpect(jsonPath("$[1].tenancy").doesNotExist())
                .andExpect(jsonPath("$[1].vacantSince").value(last.plusDays(1).toString()))
                .andExpect(jsonPath("$[1].next.id").value(next))
                .andExpect(jsonPath("$[1].next.state").value("FUTURE"))
                // Never rented.
                .andExpect(jsonPath("$[2].unit.name").value("Whg. 3"))
                .andExpect(jsonPath("$[2].tenancy").doesNotExist())
                .andExpect(jsonPath("$[2].vacantSince").doesNotExist())
                .andExpect(jsonPath("$[2].next").doesNotExist());

        // On its last day the tenancy still runs; the day after, the unit is vacant.
        anna = later(anna, 1);
        getWith(UNITS, anna.session()).andExpect(jsonPath("$[0].tenancy.id").value(running));
        anna = later(anna, 1);
        getWith(UNITS, anna.session())
                .andExpect(jsonPath("$[0].tenancy").doesNotExist())
                .andExpect(jsonPath("$[0].vacantSince").value(end.plusDays(1).toString()));
        getWith(TENANCIES + "/" + running, anna.session()).andExpect(jsonPath("$.state").value("ENDED"));

        // The future tenancy starts: the unit is rented.
        anna = later(anna, 18);
        getWith(UNITS, anna.session())
                .andExpect(jsonPath("$[1].tenancy.id").value(next))
                .andExpect(jsonPath("$[1].vacantSince").doesNotExist())
                .andExpect(jsonPath("$[1].next").doesNotExist());
    }

    @Test
    void archivedUnitsAreLeftOut() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        postJson("/api/properties/" + house[0] + "/archive", Map.of(), anna.session()).andExpect(status().isOk());
        getWith(UNITS, anna.session()).andExpect(jsonPath("$", hasSize(0)));
    }
}
