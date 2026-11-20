package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Owner use („Eigenbelegung“): recorded like a booking, but without guests, rent, review or statement. */
class OwnerUseTest extends VacationRentalTest {

    private Map<String, Object> ownerUse(Long unitId, String arrival, String departure) {
        var body = new HashMap<String, Object>(Map.of("unitId", unitId, "arrival", arrival, "departure", departure,
                "kind", "OWNER"));
        // Guest fields are ignored for an owner use.
        body.put("adults", 3);
        body.put("expectedRent", "100.00");
        body.put("guest", Map.of("name", "Erika Musterfrau"));
        return body;
    }

    @Test
    void recordOwnerUseByHandAndItBlocksThePeriod() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var id = id(book(anna, ownerUse(unit, "2027-06-09", "2027-06-14"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("OWNER"))
                .andExpect(jsonPath("$.nights").value(5))
                .andExpect(jsonPath("$.adults").value(0))
                .andExpect(jsonPath("$.expectedRent").doesNotExist())
                .andExpect(jsonPath("$.guest.name").doesNotExist())
                .andExpect(jsonPath("$.reviewState").value("NONE")));

        book(anna, stay(unit, "2027-06-12", "2027-06-15"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist in diesem Zeitraum bereits belegt"));

        var guest = booking(anna, unit, "2027-07-01", "2027-07-03");
        getWith("/api/vacationrental/bookings?kind=OWNER", anna.session())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));
        getWith("/api/vacationrental/bookings?kind=GUEST", anna.session())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(guest));

        var change = stay(unit, "2027-07-01", "2027-07-03");
        change.put("kind", "OWNER");
        change.put("version", 0);
        putJson("/api/vacationrental/bookings/" + guest, change, anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Art einer Buchung kann nicht geändert werden."));
    }

    @Test
    void ownerUseIsNotSettled() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var id = id(book(anna, ownerUse(unit, "2026-11-10", "2026-11-12")).andExpect(status().isCreated()));
        var statement = id(postJson("/api/vacationrental/statements", Map.of("unitId", unit, "provider", "Nord",
                "periodFrom", "2026-11-01", "periodTo", "2026-11-30", "statementDate", "2026-11-30"), anna.session())
                .andExpect(status().isCreated()));
        putJson("/api/vacationrental/statements/" + statement + "/lines/" + id, Map.of("gross", "10.00"),
                anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Eine Eigenbelegung wird nicht abgerechnet"));
    }

    @Test
    void overviewShowsOwnerNightsOnTheirOwn() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        booking(anna, unit, "2027-06-01", "2027-06-05");                          // 4 nights
        book(anna, ownerUse(unit, "2027-06-09", "2027-06-14")).andExpect(status().isCreated());   // 5 nights
        getWith("/api/vacationrental/occupancy/overview?unitId=" + unit + "&year=2027", anna.session())
                .andExpect(jsonPath("$.months[5].nights").value(4))
                .andExpect(jsonPath("$.months[5].occupancy").value(13))
                .andExpect(jsonPath("$.months[5].ownerNights").value(5))
                .andExpect(jsonPath("$.total.ownerNights").value(5));
    }

    @Test
    void feedTitlesOwnerUses() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var arrival = LocalDate.now().plusDays(10);
        book(anna, ownerUse(unit, arrival.toString(), arrival.plusDays(5).toString())).andExpect(status().isCreated());
        for (var withGuests : new boolean[] {false, true}) {
            var path = json.readTree(postJson("/api/vacationrental/calendar-links",
                    Map.of("unitId", unit, "name", "Reinigung", "withGuests", withGuests), anna.session())
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                    .get("path").asText();
            var feed = mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
            assertThat(feed).contains("SUMMARY:Eigenbelegung\r\n").doesNotContain("Erika", "?");
        }
    }
}
