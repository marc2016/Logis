package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
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
        var property = singleUnitProperty(anna, "Haus Waldeck");
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
                .andExpect(jsonPath("$.months[10].settledNet").value("540.00"))
                .andExpect(jsonPath("$.months[10].plannedRent").value("248.58"))
                .andExpect(jsonPath("$.total.nights").value(11));
        getWith(url, dieter.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[10].settledNet").value("540.00"));
    }

    @Test
    void moneyCountsOnTheStatementDate() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var stay = booking(anna, unit, "2026-03-28", "2026-04-04");
        settle(anna, statement(anna, unit, "2026-04-05"), stay, "700.00");
        var cancelled = booking(anna, unit, "2026-05-10", "2026-05-12");
        postJson("/api/vacationrental/bookings/" + cancelled + "/cancel", Map.of(), anna.session());
        settle(anna, statement(anna, unit, "2026-06-03"), cancelled, "80.00");

        getWith("/api/vacationrental/occupancy/overview?unitId=" + unit + "&year=2026", anna.session())
                .andExpect(jsonPath("$.months[2].nights").value(4))
                .andExpect(jsonPath("$.months[2].settledNet").value("0.00"))
                .andExpect(jsonPath("$.months[3].nights").value(3))
                .andExpect(jsonPath("$.months[3].settledNet").value("700.00"))
                .andExpect(jsonPath("$.months[4].nights").value(0))
                .andExpect(jsonPath("$.months[5].settledNet").value("80.00"))
                .andExpect(jsonPath("$.total.settledNet").value("780.00"));
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
        var october = booking(anna, ids[1], "2026-10-30", "2026-11-01");

        var url = "/api/vacationrental/occupancy/calendar?propertyId=" + ids[0] + "&year=2026";
        getWith(url, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.from").value("2025-12"))
                .andExpect(jsonPath("$.to").value("2027-01"))
                .andExpect(jsonPath("$.units[*].name", contains("Whg. 1", "Whg. 2")))
                .andExpect(jsonPath("$.units[0].bookings[*].id", contains(october.intValue(), planned.intValue())))
                .andExpect(jsonPath("$.units[0].bookings[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.units[1].bookings[0].status").value("SETTLED"));
        getWith(url, carla.session())
                .andExpect(jsonPath("$.units[*].name", contains("Whg. 2")))
                .andExpect(jsonPath("$.units[0].canEdit").value(true));
        getWith(url, bert.session()).andExpect(status().isNotFound());
        getWith("/api/vacationrental/occupancy/overview?unitId=" + ids[1] + "&year=2026", carla.session())
                .andExpect(status().isNotFound());
    }

    @Test
    void calendarYearIncludesTheNeighbouringMonths() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Waldeck");
        var unit = unitIds(property).getFirst();
        var newYear = booking(anna, unit, "2026-12-30", "2027-01-02");
        var earlyDecember = booking(anna, unit, "2025-12-01", "2025-12-03");
        var lateJanuary = booking(anna, unit, "2027-01-30", "2027-02-02");
        var outside = booking(anna, unit, "2025-11-20", "2025-11-30");

        var base = "/api/vacationrental/occupancy/calendar?propertyId=" + property + "&year=";
        getWith(base + 2026, anna.session())
                .andExpect(jsonPath("$.units[0].bookings[*].id",
                        contains(earlyDecember.intValue(), newYear.intValue(), lateJanuary.intValue())));
        getWith(base + 2027, anna.session())
                .andExpect(jsonPath("$.from").value("2026-12"))
                .andExpect(jsonPath("$.units[0].bookings[*].id", contains(newYear.intValue(), lateJanuary.intValue())));
        getWith(base + 2025, anna.session())
                .andExpect(jsonPath("$.units[0].bookings[*].id", hasItem(outside.intValue())));
        getWith(base + 1800, anna.session()).andExpect(status().isBadRequest());
        getWith("/api/vacationrental/occupancy/calendar?propertyId=" + property + "&month=2026-11", anna.session())
                .andExpect(status().isBadRequest());
    }

    // --- occupancy-charts: all units ----------------------------------------------------------------

    private com.fasterxml.jackson.databind.JsonNode unitsOverview(Actor actor, int year) throws Exception {
        return json.readTree(getWith("/api/vacationrental/occupancy/overview/units?year=" + year, actor.session())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void allVisibleUnitsAcrossProperties() throws Exception {
        var anna = actor();
        var dieter = actor();
        var waldeck = singleUnitProperty(anna, "Zz Haus Waldeck");
        var waldeckUnit = unitIds(waldeck).getFirst();
        var flats = twoUnitProperty(anna);
        var stay = booking(anna, waldeckUnit, "2026-11-10", "2026-11-19");
        settle(anna, statement(anna, waldeckUnit, "2026-11-30"), stay, "540.00");
        booking(anna, flats[1], "2026-07-01", "2026-07-08");
        share(anna, "property", waldeck, dieter, "VIEWER");
        // Someone else's unit never shows up.
        singleUnitProperty(actor(), "Fremdes Haus");

        var all = unitsOverview(anna, 2026);
        assertThat(all).hasSize(3);
        assertThat(all.get(2).get("unitId").asLong()).isEqualTo(waldeckUnit);
        assertThat(all.get(2).get("propertyName").asText()).isEqualTo("Zz Haus Waldeck");
        assertThat(all.get(0).get("unitName").asText()).isEqualTo("Whg. 1");
        assertThat(all.get(1).get("unitName").asText()).isEqualTo("Whg. 2");

        // Each total equals the unit's own overview.
        for (var row : all) {
            var single = json.readTree(getWith("/api/vacationrental/occupancy/overview?unitId=" + row.get("unitId").asLong()
                    + "&year=2026", anna.session()).andReturn().getResponse().getContentAsString());
            assertThat(row.get("total")).isEqualTo(single.get("total"));
        }
        assertThat(all.get(2).get("total").get("nights").asInt()).isEqualTo(9);
        assertThat(all.get(2).get("total").get("settledNet").asText()).isEqualTo("540.00");
        assertThat(all.get(0).get("total").get("nights").asInt()).isEqualTo(7);

        var shared = unitsOverview(dieter, 2026);
        assertThat(shared).hasSize(1);
        assertThat(shared.get(0).get("unitId").asLong()).isEqualTo(waldeckUnit);
    }

    @Test
    void archivedUnitsOnlyWithFigures() throws Exception {
        var anna = actor();
        var used = singleUnitProperty(anna, "Altes Haus");
        var usedUnit = unitIds(used).getFirst();
        booking(anna, usedUnit, "2026-07-01", "2026-07-08");
        var unused = singleUnitProperty(anna, "Leeres Haus");
        postJson("/api/properties/" + used + "/archive", Map.of(), anna.session()).andExpect(status().isOk());
        postJson("/api/properties/" + unused + "/archive", Map.of(), anna.session()).andExpect(status().isOk());

        var in2026 = unitsOverview(anna, 2026);
        assertThat(in2026).hasSize(1);
        assertThat(in2026.get(0).get("unitId").asLong()).isEqualTo(usedUnit);
        assertThat(in2026.get(0).get("archived").asBoolean()).isTrue();
        assertThat(unitsOverview(anna, 2025)).isEmpty();
    }

    @Test
    void unitsOverviewNeedsAValidYear() throws Exception {
        getWith("/api/vacationrental/occupancy/overview/units?year=12", actor().session())
                .andExpect(status().isBadRequest());
    }
}
