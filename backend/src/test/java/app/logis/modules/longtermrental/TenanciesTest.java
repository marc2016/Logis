package app.logis.modules.longtermrental;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenanciesTest extends LongTermRentalTest {

    @Test
    void createAndRead() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var start = today().plusDays(10);
        var body = body(house[1], start, null, "650.00");
        body.put("tenants", List.of(tenant("Erika Musterfrau"), Map.of("name", " Max Mustermann ")));
        body.put("depositAmount", "1950.00");
        var id = id(postJson(TENANCIES, body, anna.session()).andExpect(status().isCreated()));

        getWith(TENANCIES + "/" + id, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit.name").value("Whg. 1"))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.state").value("FUTURE"))
                .andExpect(jsonPath("$.tenants", hasSize(2)))
                .andExpect(jsonPath("$.tenants[0].name").value("Erika Musterfrau"))
                .andExpect(jsonPath("$.tenants[0].email").value("mieter@example.org"))
                .andExpect(jsonPath("$.tenants[1].name").value("Max Mustermann"))
                .andExpect(jsonPath("$.depositAmount").value("1950.00"))
                .andExpect(jsonPath("$.depositReceivedOn").doesNotExist())
                .andExpect(jsonPath("$.currentRent.coldRent").value("650.00"))
                .andExpect(jsonPath("$.currentRent.utilitiesAdvance").value("150.00"))
                .andExpect(jsonPath("$.currentRent.total").value("800.00"))
                .andExpect(jsonPath("$.rentTerms", hasSize(1)))
                .andExpect(jsonPath("$.rentTerms[0].validFrom").value(start.toString()))
                .andExpect(jsonPath("$.canEdit").value(true));
        getWith(TENANCIES + "?unitId=" + house[1], anna.session())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
        getWith(TENANCIES + "?unitId=" + house[1] + "&state=RUNNING&state=ENDED", anna.session())
                .andExpect(jsonPath("$", hasSize(0)));
        getWith(TENANCIES + "?propertyId=" + house[0] + "&state=FUTURE", anna.session())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var holiday = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();

        create(anna, holiday, today(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist keine Dauermiete"));
        create(anna, house[1], today(), today().minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das Ende liegt vor dem Beginn"));
        var noTenant = body(house[1], today(), null, "650.00");
        noTenant.put("tenants", List.of(Map.of("name", " ")));
        postJson(TENANCIES, noTenant, anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bitte mindestens einen Mieter mit Namen angeben."));
        var noRent = body(house[1], today(), null, "650.00");
        noRent.remove("rent");
        postJson(TENANCIES, noRent, anna.session()).andExpect(status().isBadRequest());
        postJson(TENANCIES, body(house[1], today(), null, "-1.00"), anna.session()).andExpect(status().isBadRequest());
        getWith(TENANCIES + "?unitId=" + house[1], anna.session()).andExpect(jsonPath("$", hasSize(0)));
        // A tenancy of a single day is fine.
        create(anna, house[1], today(), today()).andExpect(status().isCreated());
    }

    @Test
    void oneTenancyPerUnitAtATime() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var start = today();
        var first = tenancy(anna, house[1], start, null);

        create(anna, house[1], start.plusMonths(3), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist in diesem Zeitraum bereits vermietet"));
        create(anna, house[1], start.minusMonths(3), start)
                .andExpect(status().isConflict());
        // The other unit is free.
        create(anna, house[2], start.plusMonths(3), null).andExpect(status().isCreated());

        // Ended on a day, the unit can be rented again from the next day.
        var end = start.plusMonths(3).minusDays(1);
        var change = body(house[1], start, end, "650.00");
        change.put("version", 0);
        putJson(TENANCIES + "/" + first, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(end.toString()));
        create(anna, house[1], end, null).andExpect(status().isConflict());
        var second = tenancy(anna, house[1], end.plusDays(1), null);

        // Extending the first one into the second is rejected as well.
        change.put("endDate", end.plusDays(1).toString());
        change.put("version", 1);
        putJson(TENANCIES + "/" + first, change, anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist in diesem Zeitraum bereits vermietet"));
        getWith(TENANCIES + "?unitId=" + house[1], anna.session())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second));
    }

    @Test
    void theDatabaseRejectsOverlapsToo() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        tenancy(anna, house[1], today(), today().plusDays(30));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO longtermrental.tenancies (unit_id, start_date, end_date) VALUES (?, ?, NULL)",
                house[1], today().plusDays(30)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void changeWithOptimisticLocking() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var start = today().minusDays(20);
        var id = tenancy(anna, house[1], start, null);

        var change = new HashMap<String, Object>(body(house[1], start.minusDays(5), null, "0"));
        change.remove("rent");
        change.put("tenants", List.of(Map.of("name", "Max Mustermann", "phone", "+49 000 1111")));
        change.put("depositAmount", "1950.00");
        change.put("depositReceivedOn", start.plusDays(4).toString());
        change.put("version", 0);
        putJson(TENANCIES + "/" + id, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.startDate").value(start.minusDays(5).toString()))
                .andExpect(jsonPath("$.tenants", hasSize(1)))
                .andExpect(jsonPath("$.tenants[0].name").value("Max Mustermann"))
                .andExpect(jsonPath("$.tenants[0].email").doesNotExist())
                .andExpect(jsonPath("$.depositReceivedOn").value(start.plusDays(4).toString()))
                // The first rent follows the start.
                .andExpect(jsonPath("$.rentTerms[0].validFrom").value(start.minusDays(5).toString()))
                .andExpect(jsonPath("$.currentRent.coldRent").value("650.00"));

        putJson(TENANCIES + "/" + id, change, anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Mietverhältnis wurde inzwischen geändert"));
        change.put("version", 1);
        change.put("tenants", List.of());
        putJson(TENANCIES + "/" + id, change, anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bitte mindestens einen Mieter mit Namen angeben."));

        // The unit can be changed while the new one is free and a long-term rental.
        change.put("tenants", List.of(tenant("Max Mustermann")));
        change.put("unitId", house[2]);
        putJson(TENANCIES + "/" + id, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit.name").value("Whg. 2"));
    }

    @Test
    void deleteRemovesTenantsAndRent() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var id = tenancy(anna, house[1], today(), null);
        deleteWith(TENANCIES + "/" + id, anna.session()).andExpect(status().isNoContent());

        getWith(TENANCIES + "/" + id, anna.session())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Mietverhältnis nicht gefunden"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM longtermrental.tenants WHERE tenancy_id = ?",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM longtermrental.rent_terms WHERE tenancy_id = ?",
                Integer.class, id)).isZero();
        create(anna, house[1], today(), null).andExpect(status().isCreated());
    }

    @Test
    void rightsFollowTheUnit() throws Exception {
        var anna = actor();
        var bert = actor();
        var carla = actor();
        var emil = actor();
        var house = rentalHouse(anna);
        var first = tenancy(anna, house[1], today(), null);
        var second = tenancy(anna, house[2], today(), null);
        share(anna, "property", house[0], carla, "VIEWER");
        share(anna, "unit", house[2], emil, "EDITOR");

        // No role: the tenancy does not exist.
        getWith(TENANCIES + "/" + first, bert.session())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Mietverhältnis nicht gefunden"));
        getWith(TENANCIES, bert.session()).andExpect(jsonPath("$", hasSize(0)));
        create(bert, house[1], today().plusYears(5), null).andExpect(status().isNotFound());
        deleteWith(TENANCIES + "/" + first, bert.session()).andExpect(status().isNotFound());

        // A viewer reads names, rent and deposit, but no contact data, and changes nothing.
        getWith(TENANCIES + "/" + first, carla.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.tenants[0].name").value("Erika Musterfrau"))
                .andExpect(jsonPath("$.tenants[0].phone").doesNotExist())
                .andExpect(jsonPath("$.tenants[0].email").doesNotExist())
                .andExpect(jsonPath("$.tenants[0].street").doesNotExist())
                .andExpect(jsonPath("$.currentRent.total").value("800.00"));
        getWith(TENANCIES, carla.session())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tenants[0].email").doesNotExist());
        var change = body(house[1], today(), null, "650.00");
        change.put("version", 0);
        putJson(TENANCIES + "/" + first, change, carla.session()).andExpect(status().isForbidden());
        deleteWith(TENANCIES + "/" + first, carla.session()).andExpect(status().isForbidden());
        postJson(TENANCIES + "/" + first + "/rent-terms",
                Map.of("validFrom", today().plusDays(1).toString(), "coldRent", "1.00"), carla.session())
                .andExpect(status().isForbidden());
        create(carla, house[1], today().plusYears(5), null).andExpect(status().isForbidden());

        // An editor of one unit sees and changes only that unit's tenancies.
        getWith(TENANCIES, emil.session())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].tenants[0].email").value("mieter@example.org"))
                .andExpect(jsonPath("$[0].canEdit").value(true));
        getWith(TENANCIES + "/" + first, emil.session()).andExpect(status().isNotFound());
        getWith("/api/longtermrental/units", emil.session())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].unit.name").value("Whg. 2"));
        // Moving a tenancy to a unit without rights is not possible.
        var move = body(house[1], today().plusYears(5), null, "650.00");
        move.put("version", 0);
        putJson(TENANCIES + "/" + second, move, emil.session()).andExpect(status().isNotFound());
    }

    @Test
    void stateFollowsTheGermanDayAfterMidnight() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        // 15 January 2027, 00:30 in Germany; in UTC it is still 14 January. Sessions do not live that long.
        clock.set(java.time.Instant.parse("2027-01-14T23:30:00Z"));
        anna = new Actor(anna.user(), login(anna.user()));
        var starting = tenancy(anna, house[1], java.time.LocalDate.parse("2027-01-15"), null);
        var ended = tenancy(anna, house[2], java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2027-01-14"));
        getWith(TENANCIES + "/" + starting, anna.session()).andExpect(jsonPath("$.state").value("RUNNING"));
        getWith(TENANCIES + "/" + ended, anna.session()).andExpect(jsonPath("$.state").value("ENDED"));
    }
}
