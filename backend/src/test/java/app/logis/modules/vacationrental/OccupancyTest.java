package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OccupancyTest extends VacationRentalTest {

    private Long statement(Actor actor, Long unitId, String date) throws Exception {
        return id(postJson("/api/vacationrental/statements", Map.of("unitId", unitId, "provider", "Nord",
                "periodFrom", date.substring(0, 8) + "01", "periodTo", date, "statementDate", date), actor.session())
                .andExpect(status().isCreated()));
    }

    private void settle(Actor actor, Long statementId, Long bookingId, String gross) throws Exception {
        putJson("/api/vacationrental/statements/" + statementId + "/lines/" + bookingId, Map.of("gross", gross),
                actor.session()).andExpect(status().isOk());
    }

    @Test
    void mixedMonth() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var unit = unitIds(property).getFirst();
        var settledStay = booking(anna, unit, "2026-11-10", "2026-11-19");      // 9 nights
        settle(anna, statement(anna, unit, "2026-11-30"), settledStay, "540.00");
        var planned = stay(unit, "2026-11-06", "2026-11-08");                   // 2 nights
        planned.put("expectedRent", "248.58");
        book(anna, planned).andExpect(status().isCreated());

        var url = "/api/vacationrental/occupancy/overview?unitId=" + unit + "&year=2026";
        getWith(url, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.months[10].nights").value(11))
                .andExpect(jsonPath("$.months[10].occupancy").value(37))
                .andExpect(jsonPath("$.months[10].settledNet").value(540.00))
                .andExpect(jsonPath("$.months[10].plannedRent").value(248.58))
                .andExpect(jsonPath("$.total.nights").value(11));
        getWith(url, dieter.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[10].settledNet").value(540.00));
    }

    @Test
    void moneyCountsOnTheStatementDate() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var stay = booking(anna, unit, "2026-03-28", "2026-04-04");
        settle(anna, statement(anna, unit, "2026-04-05"), stay, "700.00");
        var cancelled = booking(anna, unit, "2026-05-10", "2026-05-12");
        postJson("/api/vacationrental/bookings/" + cancelled + "/cancel", Map.of(), anna.session());
        settle(anna, statement(anna, unit, "2026-06-03"), cancelled, "80.00");

        getWith("/api/vacationrental/occupancy/overview?unitId=" + unit + "&year=2026", anna.session())
                .andExpect(jsonPath("$.months[2].nights").value(4))
                .andExpect(jsonPath("$.months[2].settledNet").value(0.00))
                .andExpect(jsonPath("$.months[3].nights").value(3))
                .andExpect(jsonPath("$.months[3].settledNet").value(700.00))
                .andExpect(jsonPath("$.months[4].nights").value(0))
                .andExpect(jsonPath("$.months[5].settledNet").value(80.00))
                .andExpect(jsonPath("$.total.settledNet").value(780.00));
    }

    @Test
    void calendarRowsAndBars() throws Exception {
        var anna = actor();
        var carla = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");
        var planned = booking(anna, ids[1], "2026-11-06", "2026-11-08");
        var settled = booking(anna, ids[2], "2026-11-10", "2026-11-12");
        settle(anna, statement(anna, ids[2], "2026-11-30"), settled, "100.00");
        var cancelled = booking(anna, ids[1], "2026-11-20", "2026-11-22");
        postJson("/api/vacationrental/bookings/" + cancelled + "/cancel", Map.of(), anna.session());
        booking(anna, ids[1], "2026-10-30", "2026-11-01"); // no night in November

        var url = "/api/vacationrental/occupancy/calendar?propertyId=" + ids[0] + "&month=2026-11";
        getWith(url, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units[*].name", contains("Whg. 1", "Whg. 2")))
                .andExpect(jsonPath("$.units[0].bookings[*].id", contains(planned.intValue())))
                .andExpect(jsonPath("$.units[0].bookings[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.units[1].bookings[0].status").value("SETTLED"));
        getWith(url, carla.session())
                .andExpect(jsonPath("$.units[*].name", contains("Whg. 2")))
                .andExpect(jsonPath("$.units[0].canEdit").value(true));
        getWith(url, bert.session()).andExpect(status().isNotFound());
        getWith("/api/vacationrental/occupancy/overview?unitId=" + ids[1] + "&year=2026", carla.session())
                .andExpect(status().isNotFound());
    }
}
