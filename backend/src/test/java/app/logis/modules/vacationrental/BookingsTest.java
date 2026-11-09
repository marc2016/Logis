package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingsTest extends VacationRentalTest {

    private Map<String, Object> confirmation(Long unitId) {
        var body = stay(unitId, "2026-11-06", "2026-11-08");
        body.put("externalRef", "250905");
        body.put("adults", 4);
        body.put("expectedRent", "248.58");
        body.put("guest", Map.of("name", "Max Gast", "street", "Seeweg 1", "postalCode", "48529",
                "city", "Nordhorn", "country", "Deutschland", "phone", "0123 456", "email", "gast@example.org"));
        return body;
    }

    @Test
    void bookingFromTheConfirmation() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        book(anna, confirmation(unit))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.expectedRent").value("248.58"))
                .andExpect(jsonPath("$.unit.propertyName").value("Haus Seeufer"))
                .andExpect(jsonPath("$.guest.email").value("gast@example.org"))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void onlyActiveVacationRentalUnits() throws Exception {
        var anna = actor();
        var property = id(createProperty(anna, Map.of("name", "Mietshaus", "multiUnit", false,
                "firstUnit", Map.of("usage", "LONG_TERM_RENTAL"))));
        book(anna, stay(unitIds(property).getFirst(), "2026-11-06", "2026-11-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Einheit wird nicht als Ferienwohnung genutzt"));
        var ids = twoUnitProperty(anna);
        postJson("/api/units/" + ids[2] + "/archive", Map.of(), anna.session()).andExpect(status().isOk());
        book(anna, stay(ids[2], "2026-11-06", "2026-11-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist archiviert"));
        book(anna, stay(ids[1], "2026-11-08", "2026-11-06")).andExpect(status().isBadRequest());
    }

    @Test
    void overlapAndChangeoverDay() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        booking(anna, unit, "2026-11-06", "2026-11-08");
        book(anna, stay(unit, "2026-11-07", "2026-11-10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist in diesem Zeitraum bereits belegt"));
        book(anna, stay(unit, "2026-11-08", "2026-11-10")).andExpect(status().isCreated());
    }

    @Test
    void bookingNumberIsUniquePerUnit() throws Exception {
        var anna = actor();
        var ids = twoUnitProperty(anna);
        book(anna, confirmation(ids[1])).andExpect(status().isCreated());
        var again = confirmation(ids[1]);
        again.put("arrival", "2026-12-01");
        again.put("departure", "2026-12-03");
        book(anna, again)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Buchungsnummer ist bereits erfasst"));
        again.put("unitId", ids[2]);
        book(anna, again).andExpect(status().isCreated());
    }

    @Test
    void cancelFreesThePeriodAndReinstateChecksIt() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var first = booking(anna, unit, "2026-11-06", "2026-11-08");
        postJson("/api/vacationrental/bookings/" + first + "/cancel", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        booking(anna, unit, "2026-11-06", "2026-11-08");
        postJson("/api/vacationrental/bookings/" + first + "/reinstate", Map.of(), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist in diesem Zeitraum bereits belegt"));
    }

    @Test
    void editAndDelete() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var id = booking(anna, unit, "2026-11-06", "2026-11-08");
        var change = stay(unit, "2026-11-06", "2026-11-09");
        change.put("version", 0);
        putJson("/api/vacationrental/bookings/" + id, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nights").value(3));
        putJson("/api/vacationrental/bookings/" + id, change, anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Buchung wurde inzwischen geändert"));

        var settled = booking(anna, unit, "2026-12-01", "2026-12-03");
        settleDirectly(unit, settled);
        getWith("/api/vacationrental/bookings/" + settled, anna.session())
                .andExpect(jsonPath("$.status").value("SETTLED"));
        mvc.perform(delete("/api/vacationrental/bookings/" + settled).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Buchung ist abgerechnet und kann nur storniert werden"));
        mvc.perform(delete("/api/vacationrental/bookings/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith("/api/vacationrental/bookings/" + id, anna.session()).andExpect(status().isNotFound());
    }

    @Test
    void rightsAndGuestDataVisibility() throws Exception {
        var anna = actor();
        var carla = actor();
        var dieter = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");
        share(anna, "property", ids[0], dieter, "VIEWER");
        var onUnit1 = id(book(anna, confirmation(ids[1])));
        var onUnit2 = booking(anna, ids[2], "2026-11-06", "2026-11-08");

        getWith("/api/vacationrental/bookings/" + onUnit1, dieter.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest.name").value("Max Gast"))
                .andExpect(jsonPath("$.guest.street").doesNotExist())
                .andExpect(jsonPath("$.guest.phone").doesNotExist())
                .andExpect(jsonPath("$.guest.email").doesNotExist())
                .andExpect(jsonPath("$.canEdit").value(false));
        book(dieter, stay(ids[1], "2027-01-01", "2027-01-03")).andExpect(status().isForbidden());

        getWith("/api/vacationrental/bookings?propertyId=" + ids[0], carla.session())
                .andExpect(jsonPath("$.items[*].id", contains(onUnit2.intValue())));
        getWith("/api/vacationrental/bookings/" + onUnit1, carla.session()).andExpect(status().isNotFound());
        getWith("/api/vacationrental/bookings/" + onUnit1, bert.session()).andExpect(status().isNotFound());
        getWith("/api/vacationrental/bookings", bert.session()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void deleteGuestData() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var id = id(book(anna, confirmation(unit)));
        mvc.perform(delete("/api/vacationrental/bookings/" + id + "/guest").with(csrf()).session(anna.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestDeleted").value(true))
                .andExpect(jsonPath("$.guest.name").doesNotExist())
                .andExpect(jsonPath("$.guest.email").doesNotExist())
                .andExpect(jsonPath("$.externalRef").value("250905"))
                .andExpect(jsonPath("$.adults").value(4))
                .andExpect(jsonPath("$.expectedRent").value("248.58"));
        var stored = jdbc.queryForMap("SELECT guest_name, guest_street, guest_phone, guest_email "
                + "FROM vacationrental.bookings WHERE id = ?", id);
        org.assertj.core.api.Assertions.assertThat(stored.values()).containsOnlyNulls();
    }

    @Test
    void listFiltersSearchAndSorting() throws Exception {
        var anna = actor();
        var ids = twoUnitProperty(anna);
        var a = id(book(anna, confirmation(ids[1])));                         // 06.-08.11., Whg. 1, Max Gast
        var b = booking(anna, ids[2], "2026-10-01", "2026-10-05");            // 4 nights, Whg. 2
        var c = booking(anna, ids[1], "2026-12-20", "2026-12-21");            // 1 night
        postJson("/api/vacationrental/bookings/" + c + "/cancel", Map.of(), anna.session()).andExpect(status().isOk());
        var base = "/api/vacationrental/bookings?propertyId=" + ids[0];

        getWith(base, anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(c.intValue(), a.intValue(), b.intValue())));
        getWith(base + "&sort=arrival,asc", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(b.intValue(), a.intValue(), c.intValue())));
        getWith(base + "&sort=departure,asc", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(b.intValue(), a.intValue(), c.intValue())));
        getWith(base + "&sort=nights,desc", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(b.intValue(), a.intValue(), c.intValue())));
        getWith(base + "&sort=expectedRent,desc", anna.session())
                .andExpect(jsonPath("$.items[0].id").value(a));
        getWith(base + "&sort=unit,asc", anna.session())
                .andExpect(jsonPath("$.items[2].id").value(b));
        getWith(base + "&sort=guest,desc", anna.session())
                .andExpect(jsonPath("$.items[0].id").value(a));
        getWith(base + "&sort=status,asc", anna.session())
                .andExpect(jsonPath("$.items[2].id").value(c));
        getWith(base + "&sort=bogus,asc", anna.session()).andExpect(status().isBadRequest());

        getWith(base + "&status=PLANNED&unitId=" + ids[1] + "&sort=arrival,asc", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(a.intValue())));
        getWith(base + "&from=2026-11-07&to=2026-11-30", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(a.intValue())));
        getWith(base + "&q=250905", anna.session()).andExpect(jsonPath("$.items", hasSize(1)));
        getWith(base + "&q=max gast", anna.session()).andExpect(jsonPath("$.items[0].id").value(a));
        // Contact data is never searched.
        getWith(base + "&q=nordhorn", anna.session()).andExpect(jsonPath("$.totalItems").value(0));
        getWith(base + "&size=2&page=1", anna.session())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void rentalUnitsForPickers() throws Exception {
        var anna = actor();
        var ids = twoUnitProperty(anna);
        id(createProperty(anna, Map.of("name", "Mietshaus", "multiUnit", false,
                "firstUnit", Map.of("usage", "LONG_TERM_RENTAL"))));
        getWith("/api/vacationrental/units", anna.session())
                .andExpect(jsonPath("$[*].id", contains((int) ids[1], (int) ids[2])))
                .andExpect(jsonPath("$[0].myRole").value("OWNER"));
    }
}
