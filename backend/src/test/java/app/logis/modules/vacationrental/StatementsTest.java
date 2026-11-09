package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatementsTest extends VacationRentalTest {

    private Long statement(Actor actor, Long unitId, String provider, String from, String to) throws Exception {
        return id(postJson("/api/vacationrental/statements", Map.of("unitId", unitId, "provider", provider,
                "periodFrom", from, "periodTo", to, "statementDate", to), actor.session())
                .andExpect(status().isCreated()));
    }

    private ResultActions line(MockHttpSession session, Long statementId, Long bookingId, Map<String, Object> body)
            throws Exception {
        return putJson("/api/vacationrental/statements/" + statementId + "/lines/" + bookingId, body, session);
    }

    private Long confirmation(Actor anna, Long unit) throws Exception {
        var body = stay(unit, "2026-11-06", "2026-11-08");
        body.put("externalRef", "250905");
        body.put("expectedRent", "248.58");
        return id(book(anna, body).andExpect(status().isCreated()));
    }

    @Test
    void createWithProviderSuggestions() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        postJson("/api/vacationrental/statements", Map.of("unitId", unit, "provider", "Ferienwohnungen Nord",
                "periodFrom", "2026-10-01", "periodTo", "2026-10-31", "statementDate", "2026-11-05"), anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payout").value("0.00"))
                .andExpect(jsonPath("$.provider").value("Ferienwohnungen Nord"));
        postJson("/api/vacationrental/statements", Map.of("unitId", unit, "provider", " ",
                "periodFrom", "2026-10-01", "periodTo", "2026-10-31", "statementDate", "2026-11-05"), anna.session())
                .andExpect(status().isBadRequest());
        getWith("/api/vacationrental/statements/providers?unitId=" + unit, anna.session())
                .andExpect(jsonPath("$", contains("Ferienwohnungen Nord")));
    }

    @Test
    void assignBookingComputesNetAndPayout() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var booking = confirmation(anna, unit);
        var november = statement(anna, unit, "Ferienwohnungen Nord", "2026-11-01", "2026-11-30");

        // Gross defaults to the expected rent.
        line(anna.session(), november, booking, Map.of("commission", "37.29", "cleaning", "60.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].gross").value("248.58"))
                .andExpect(jsonPath("$.lines[0].net").value("151.29"))
                .andExpect(jsonPath("$.payout").value("151.29"));
        getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.statementId").value(november));
    }

    @Test
    void assignmentRules() throws Exception {
        var anna = actor();
        var ids = twoUnitProperty(anna);
        var onUnit1 = booking(anna, ids[1], "2026-11-06", "2026-11-08");
        var forUnit2 = statement(anna, ids[2], "Nord", "2026-11-01", "2026-11-30");
        line(anna.session(), forUnit2, onUnit1, Map.of("gross", "100.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Buchung gehört zu einer anderen Einheit"));

        var first = statement(anna, ids[1], "Nord", "2026-11-01", "2026-11-30");
        var second = statement(anna, ids[1], "Nord", "2026-12-01", "2026-12-31");
        line(anna.session(), first, onUnit1, Map.of("gross", "100.00")).andExpect(status().isOk());
        line(anna.session(), second, onUnit1, Map.of("gross", "100.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Buchung ist bereits abgerechnet"));
        // Without an expected rent, a gross amount is required.
        var noRent = booking(anna, ids[1], "2026-12-06", "2026-12-08");
        line(anna.session(), second, noRent, Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void cancellationPayout() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var booking = booking(anna, unit, "2026-11-06", "2026-11-08");
        postJson("/api/vacationrental/bookings/" + booking + "/cancel", Map.of(), anna.session())
                .andExpect(status().isOk());
        var november = statement(anna, unit, "Nord", "2026-11-01", "2026-11-30");
        line(anna.session(), november, booking, Map.of("gross", "80.00", "commission", "12.00"))
                .andExpect(jsonPath("$.payout").value("68.00"))
                .andExpect(jsonPath("$.lines[0].booking.status").value("CANCELLED"));
        // The cancelled stay does not block the calendar.
        booking(anna, unit, "2026-11-06", "2026-11-08");
    }

    @Test
    void removeLineAndDeleteStatementRevertBookings() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var a = confirmation(anna, unit);
        var b = booking(anna, unit, "2026-11-20", "2026-11-22");
        var november = statement(anna, unit, "Nord", "2026-11-01", "2026-11-30");
        line(anna.session(), november, a, Map.of("commission", "37.29", "cleaning", "60.00"));
        line(anna.session(), november, b, Map.of("gross", "100.00")).andExpect(jsonPath("$.payout").value("251.29"));

        mvc.perform(delete("/api/vacationrental/statements/" + november + "/lines/" + a)
                        .with(csrf()).session(anna.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payout").value("100.00"));
        getWith("/api/vacationrental/bookings/" + a, anna.session()).andExpect(jsonPath("$.status").value("PLANNED"));

        mvc.perform(delete("/api/vacationrental/statements/" + november).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith("/api/vacationrental/bookings/" + b, anna.session()).andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void editStatement() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var id = statement(anna, unit, "Nord", "2026-11-01", "2026-11-30");
        var change = new HashMap<String, Object>(Map.of("unitId", unit, "provider", "Nord GmbH",
                "periodFrom", "2026-11-01", "periodTo", "2026-11-30", "statementDate", "2026-12-02", "version", 0));
        putJson("/api/vacationrental/statements/" + id, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("Nord GmbH"))
                .andExpect(jsonPath("$.version").value(1));
        putJson("/api/vacationrental/statements/" + id, change, anna.session()).andExpect(status().isConflict());
    }

    @Test
    void accessThroughTheUnit() throws Exception {
        var anna = actor();
        var carla = actor();
        var dieter = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");
        share(anna, "property", ids[0], dieter, "VIEWER");
        var body = stay(ids[1], "2026-11-06", "2026-11-08");
        body.put("guest", Map.of("name", "Max Gast", "email", "gast@example.org"));
        var booking = id(book(anna, body));
        var onUnit1 = statement(anna, ids[1], "Nord", "2026-11-01", "2026-11-30");
        statement(anna, ids[2], "Nord", "2026-11-01", "2026-11-30");
        line(anna.session(), onUnit1, booking, Map.of("gross", "100.00"));

        getWith("/api/vacationrental/statements/" + onUnit1, dieter.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.lines[0].booking.guest.name").value("Max Gast"))
                .andExpect(jsonPath("$.lines[0].booking.guest.email").doesNotExist());
        line(dieter.session(), onUnit1, booking, Map.of("gross", "1.00")).andExpect(status().isForbidden());
        getWith("/api/vacationrental/statements/" + onUnit1, carla.session()).andExpect(status().isNotFound());
        getWith("/api/vacationrental/statements", carla.session()).andExpect(jsonPath("$", hasSize(1)));
    }
}
