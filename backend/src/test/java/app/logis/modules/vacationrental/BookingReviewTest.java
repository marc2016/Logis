package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingReviewTest extends VacationRentalTest {

    private void markNew(Long bookingId) {
        jdbc.update("UPDATE vacationrental.bookings SET review_state = 'NEW' WHERE id = ?", bookingId);
    }

    private void propose(Long bookingId, String json) {
        jdbc.update("UPDATE vacationrental.bookings SET review_state = 'CHANGE', proposal = ?::jsonb WHERE id = ?",
                json, bookingId);
    }

    @Test
    void agencyAndPets() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var fewo = agency(anna, "Fewo & Meer");
        var holiduId = agency(anna, "Holidu");
        var body = stay(unit, "2026-11-06", "2026-11-08");
        body.put("externalRef", "1234");
        body.put("agencyId", fewo);
        body.put("pets", 1);
        book(anna, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.agency.id").value(fewo.intValue()))
                .andExpect(jsonPath("$.agency.name").value(org.hamcrest.Matchers.startsWith("Fewo & Meer")))
                .andExpect(jsonPath("$.pets").value(1))
                .andExpect(jsonPath("$.reviewState").value("NONE"));

        // Same number at another agency is fine, at the same agency it is not.
        var holidu = stay(unit, "2026-12-06", "2026-12-08");
        holidu.put("externalRef", "1234");
        holidu.put("agencyId", holiduId);
        book(anna, holidu).andExpect(status().isCreated());
        var again = stay(unit, "2027-01-06", "2027-01-08");
        again.put("externalRef", "1234");
        again.put("agencyId", fewo);
        book(anna, again).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Buchungsnummer ist bereits erfasst"));

        getWith("/api/vacationrental/bookings?unitId=" + unit + "&sort=agency,asc", anna.session())
                .andExpect(jsonPath("$.items[*].agency.id", contains(fewo.intValue(), holiduId.intValue())));
        getWith("/api/vacationrental/bookings?unitId=" + unit + "&agencyId=" + holiduId, anna.session())
                .andExpect(jsonPath("$.totalItems").value(1));
        // An unknown agency is rejected.
        var unknown = stay(unit, "2027-02-06", "2027-02-08");
        unknown.put("agencyId", 999_999);
        book(anna, unknown).andExpect(status().isBadRequest());
    }

    @Test
    void confirmAndFilterImportedBookings() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var imported = booking(anna, unit, "2026-12-23", "2026-12-27");
        var manual = booking(anna, unit, "2026-11-06", "2026-11-08");
        markNew(imported);

        getWith("/api/vacationrental/bookings?review=true", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(imported.intValue())));
        // Awaiting review, it already occupies the period.
        book(anna, stay(unit, "2026-12-24", "2026-12-26")).andExpect(status().isConflict());

        var change = stay(unit, "2026-12-23", "2026-12-27");
        change.put("guest", Map.of("name", "Korrigierter Name"));
        change.put("version", 0);
        putJson("/api/vacationrental/bookings/" + imported, change, anna.session()).andExpect(status().isOk());
        postJson("/api/vacationrental/bookings/" + imported + "/confirm", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewState").value("NONE"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.guest.name").value("Korrigierter Name"));
        getWith("/api/vacationrental/bookings?review=true", anna.session())
                .andExpect(jsonPath("$.totalItems").value(0));
        // Ordinary bookings cannot be discarded.
        postJson("/api/vacationrental/bookings/" + manual + "/discard", Map.of(), anna.session())
                .andExpect(status().isConflict());
    }

    @Test
    void discardDeletesTheBookingAndItsDocuments() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var unit = unitIds(property).getFirst();
        var imported = booking(anna, unit, "2026-12-23", "2026-12-27");
        markNew(imported);
        jdbc.update("INSERT INTO core.documents (owner_type, owner_id, file_name, content_type, size_bytes, content) "
                + "VALUES ('vacationrental.booking', ?, 'a.eml', 'message/rfc822', 1, '\\x41')", imported);

        postJson("/api/vacationrental/bookings/" + imported + "/discard", Map.of(), dieter.session())
                .andExpect(status().isForbidden());
        postJson("/api/vacationrental/bookings/" + imported + "/discard", Map.of(), anna.session())
                .andExpect(status().isNoContent());
        getWith("/api/vacationrental/bookings/" + imported, anna.session()).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM core.documents "
                + "WHERE owner_type = 'vacationrental.booking' AND owner_id = ?", Integer.class, imported)).isZero();
    }

    @Test
    void applyAndDiscardProposals() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var unit = unitIds(property).getFirst();
        var id = booking(anna, unit, "2026-11-06", "2026-11-08");
        booking(anna, unit, "2026-11-10", "2026-11-12");

        propose(id, "{\"type\":\"CHANGE\",\"departure\":\"2026-11-09\",\"adults\":4,"
                + "\"guest\":{\"name\":\"Gast\",\"email\":\"g@example.org\"}}");
        getWith("/api/vacationrental/bookings/" + id, anna.session())
                .andExpect(jsonPath("$.reviewState").value("CHANGE"))
                .andExpect(jsonPath("$.proposal.departure").value("2026-11-09"))
                .andExpect(jsonPath("$.proposal.guest.email").value("g@example.org"));
        getWith("/api/vacationrental/bookings/" + id, dieter.session())
                .andExpect(jsonPath("$.proposal.guest.name").value("Gast"))
                .andExpect(jsonPath("$.proposal.guest.email").doesNotExist());
        getWith("/api/vacationrental/bookings?review=true", anna.session())
                .andExpect(jsonPath("$.items[*].id", contains(id.intValue())));

        postJson("/api/vacationrental/bookings/" + id + "/proposal/apply", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departure").value("2026-11-09"))
                .andExpect(jsonPath("$.adults").value(4))
                .andExpect(jsonPath("$.guest.email").value("g@example.org"))
                .andExpect(jsonPath("$.proposal").doesNotExist())
                .andExpect(jsonPath("$.reviewState").value("NONE"));

        // A change onto an occupied period is rejected, and the proposal stays.
        propose(id, "{\"type\":\"CHANGE\",\"departure\":\"2026-11-11\"}");
        postJson("/api/vacationrental/bookings/" + id + "/proposal/apply", Map.of(), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Einheit ist in diesem Zeitraum bereits belegt"));
        getWith("/api/vacationrental/bookings/" + id, anna.session())
                .andExpect(jsonPath("$.proposal.departure").value("2026-11-11"));
        postJson("/api/vacationrental/bookings/" + id + "/proposal/discard", Map.of(), anna.session())
                .andExpect(jsonPath("$.proposal").doesNotExist())
                .andExpect(jsonPath("$.departure").value("2026-11-09"));
    }

    @Test
    void applyCancellationFreesThePeriod() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var id = booking(anna, unit, "2026-11-06", "2026-11-08");
        propose(id, "{\"type\":\"CANCELLATION\"}");
        postJson("/api/vacationrental/bookings/" + id + "/proposal/apply", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reviewState").value("NONE"));
        booking(anna, unit, "2026-11-06", "2026-11-08");
        mvc.perform(delete("/api/vacationrental/bookings/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
    }
}
