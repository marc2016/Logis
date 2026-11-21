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

    @org.springframework.beans.factory.annotation.Autowired
    StatementService service;

    private static final byte[] PDF = "%PDF-1.4\n%Testdokument\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

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
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
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
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
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
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
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
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
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
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
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

    // --- statement-mails: invoice number, stated payout, open lines, documents ---

    private Long openLine(Actor actor, Long statementId, String name, String arrival, String departure,
            String gross, String commission) {
        service.addOpenLines(actor.user().id(), statementId, java.util.List.of(new StatementService.OpenLineInput(
                name, java.time.LocalDate.parse(arrival), java.time.LocalDate.parse(departure),
                new java.math.BigDecimal(gross), new java.math.BigDecimal(commission), null)));
        return jdbc.queryForObject("SELECT max(id) FROM vacationrental.statement_open_lines WHERE statement_id = ?",
                Long.class, statementId);
    }

    @Test
    void invoiceNumberAndStatedPayout() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var id = statement(anna, unit, "Nord", "2027-03-01", "2027-03-31");
        // Existing statements have neither; nothing to review.
        getWith("/api/vacationrental/statements/" + id, anna.session())
                .andExpect(jsonPath("$.invoiceNumber").doesNotExist())
                .andExpect(jsonPath("$.statedPayout").doesNotExist())
                .andExpect(jsonPath("$.toReview").value(false));

        var change = new HashMap<String, Object>(Map.of("unitId", unit, "provider", "Nord",
                "periodFrom", "2027-03-01", "periodTo", "2027-03-31", "statementDate", "2027-04-03",
                "invoiceNumber", " R-2027-03 ", "statedPayout", "1200.00", "version", 0));
        putJson("/api/vacationrental/statements/" + id, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("R-2027-03"))
                .andExpect(jsonPath("$.statedPayout").value("1200.00"))
                .andExpect(jsonPath("$.payoutDifference").value("1200.00"))
                .andExpect(jsonPath("$.toReview").value(true));

        var booking = booking(anna, unit, "2027-03-05", "2027-03-12");
        line(anna.session(), id, booking, Map.of("gross", "1350.00", "commission", "200.00"))
                .andExpect(jsonPath("$.payoutDifference").value("50.00"))
                .andExpect(jsonPath("$.toReview").value(true));
        line(anna.session(), id, booking, Map.of("gross", "1400.00", "commission", "200.00"))
                .andExpect(jsonPath("$.payoutDifference").value("0.00"))
                .andExpect(jsonPath("$.toReview").value(false));
        getWith("/api/vacationrental/statements", anna.session())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].toReview").value(false));
    }

    @Test
    void openLines() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Waldeck");
        share(anna, "property", property, dieter, "VIEWER");
        var unit = unitIds(property).getFirst();
        var id = statement(anna, unit, "Nord", "2027-03-01", "2027-03-31");
        var erika = openLine(anna, id, "Erika Musterfrau", "2027-03-05", "2027-03-12", "400.00", "60.00");
        var extension = openLine(anna, id, "Max Mustermann", "2027-03-14", "2027-03-15", "90.00", "15.00");
        var other = openLine(anna, id, "Otto Beispiel", "2027-03-20", "2027-03-22", "200.00", "30.00");

        getWith("/api/vacationrental/statements/" + id, anna.session())
                .andExpect(jsonPath("$.openLineCount").value(3))
                .andExpect(jsonPath("$.openLines", hasSize(3)))
                .andExpect(jsonPath("$.openLines[0].guestName").value("Erika Musterfrau"))
                .andExpect(jsonPath("$.payout").value("0.00"))
                .andExpect(jsonPath("$.toReview").value(true));
        getWith("/api/vacationrental/statements", anna.session())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].openLineCount").value(3));
        // Viewers see the number only.
        getWith("/api/vacationrental/statements/" + id, dieter.session())
                .andExpect(jsonPath("$.openLineCount").value(3))
                .andExpect(jsonPath("$.openLines", hasSize(0)));
        postJson("/api/vacationrental/statements/" + id + "/open-lines/" + erika + "/assign",
                Map.of("bookingId", 1), dieter.session()).andExpect(status().isForbidden());

        // A booking on no statement gets the open line's amounts.
        var free = booking(anna, unit, "2027-03-05", "2027-03-12");
        postJson("/api/vacationrental/statements/" + id + "/open-lines/" + erika + "/assign",
                Map.of("bookingId", free), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].gross").value("400.00"))
                .andExpect(jsonPath("$.lines[0].commission").value("60.00"))
                .andExpect(jsonPath("$.lines[0].cleaning").value("0.00"))
                .andExpect(jsonPath("$.openLineCount").value(2));

        // A booking already on this statement gets the amounts added.
        var extended = booking(anna, unit, "2027-03-15", "2027-03-18");
        line(anna.session(), id, extended, Map.of("gross", "350.00", "commission", "55.00"));
        postJson("/api/vacationrental/statements/" + id + "/open-lines/" + extension + "/assign",
                Map.of("bookingId", extended), anna.session())
                .andExpect(jsonPath("$.lines[1].gross").value("440.00"))
                .andExpect(jsonPath("$.lines[1].commission").value("70.00"))
                .andExpect(jsonPath("$.openLineCount").value(1));

        // Bookings on another statement and owner uses are refused.
        var settled = booking(anna, unit, "2027-03-20", "2027-03-22");
        var february = statement(anna, unit, "Nord", "2027-02-01", "2027-02-28");
        line(anna.session(), february, settled, Map.of("gross", "100.00"));
        postJson("/api/vacationrental/statements/" + id + "/open-lines/" + other + "/assign",
                Map.of("bookingId", settled), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Buchung ist bereits abgerechnet"));
        var ownerUse = stay(unit, "2027-03-25", "2027-03-27");
        ownerUse.put("kind", "OWNER");
        var owner = id(book(anna, ownerUse).andExpect(status().isCreated()));
        postJson("/api/vacationrental/statements/" + id + "/open-lines/" + other + "/assign",
                Map.of("bookingId", owner), anna.session())
                .andExpect(status().isBadRequest());

        // „Verwerfen“ removes it without touching the payout.
        mvc.perform(delete("/api/vacationrental/statements/" + id + "/open-lines/" + other)
                        .with(csrf()).session(anna.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openLineCount").value(0))
                .andExpect(jsonPath("$.payout").value("710.00"))
                .andExpect(jsonPath("$.toReview").value(false));
        mvc.perform(delete("/api/vacationrental/statements/" + id + "/open-lines/" + other)
                        .with(csrf()).session(anna.session()))
                .andExpect(status().isNotFound());

        // Deleting the statement deletes its open lines.
        openLine(anna, id, "Erika Musterfrau", "2027-03-28", "2027-03-30", "100.00", "15.00");
        mvc.perform(delete("/api/vacationrental/statements/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM vacationrental.statement_open_lines WHERE statement_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void documentsOfAStatement() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Waldeck");
        share(anna, "property", property, dieter, "VIEWER");
        var id = statement(anna, unitIds(property).getFirst(), "Nord", "2027-03-01", "2027-03-31");
        var doc = id(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/documents")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "Abrechnung.pdf",
                                "application/pdf", PDF))
                        .param("ownerType", "vacationrental.statement").param("ownerId", id.toString())
                        .with(csrf()).session(anna.session()))
                .andExpect(status().isCreated()));
        getWith("/api/documents?ownerType=vacationrental.statement&ownerId=" + id, anna.session())
                .andExpect(jsonPath("$", hasSize(1)));
        getWith("/api/documents/" + doc, anna.session()).andExpect(status().isOk());
        getWith("/api/documents?ownerType=vacationrental.statement&ownerId=" + id, dieter.session())
                .andExpect(status().isNotFound());
        getWith("/api/documents/" + doc, dieter.session()).andExpect(status().isNotFound());

        mvc.perform(delete("/api/vacationrental/statements/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM core.documents WHERE id = ?", Integer.class, doc)).isZero();
    }
}
