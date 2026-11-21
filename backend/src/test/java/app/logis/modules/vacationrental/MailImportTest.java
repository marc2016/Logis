package app.logis.modules.vacationrental;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Flags;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MailImportTest extends VacationRentalTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    @Autowired
    app.logis.core.mail.Mails imports;

    private GreenMailUser mailUser;
    private Actor anna;
    private Long unit;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        anna = actor();
        unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        saveMailbox(anna, "geheim").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions saveMailbox(Actor actor, String password)
            throws Exception {
        var body = new HashMap<String, Object>(Map.of("host", "localhost", "port", greenMail.getImap().getPort(),
                "username", "anna", "folder", "INBOX", "useTls", false, "cutoffDate", "2026-11-01",
                "enabled", true));
        if (password != null) {
            body.put("password", password);
        }
        return mvc.perform(put("/api/mail/mailbox").with(csrf()).session(actor.session())
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private void deliver(String name, String receivedAt) throws Exception {
        try (var in = getClass().getResourceAsStream("/mails/" + name)) {
            var message = new MimeMessage(Session.getInstance(new Properties()), in);
            greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                    .appendMessage(message, new Flags(),
                            Date.from(receivedAt == null ? clock.instant() : Instant.parse(receivedAt)));
        }
    }

    /** The VVV sample with another intermediary and booking number, for agencies that only this test uses. */
    private void deliverDs(String intermediary, String ref, String arrival, String departure) throws Exception {
        deliverDs(intermediary, ref, arrival, departure, ref);
    }

    private void deliverDs(String intermediary, String ref, String arrival, String departure, String messageId)
            throws Exception {
        String text;
        try (var in = getClass().getResourceAsStream("/mails/ds-vvv-confirmation.eml")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        text = text.replace("VVV-Stadt- und Citymarketing Talstadt e.V.", intermediary)
                .replace("3e98f765kl", ref).replace("15.05.2027", arrival).replace("18.05.2027", departure);
        var message = new MimeMessage(Session.getInstance(new Properties()),
                new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        message.setHeader("Message-ID", "<" + messageId + "@example.org>");
        greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                .appendMessage(message, new Flags(), Date.from(clock.instant()));
    }

    private Long bookingOf(String subjectPart) throws Exception {
        return ((Number) inboxItem(anna, subjectPart).get("linkId")).longValue();
    }

    private Long agencyIdByName(String name) {
        return jdbc.queryForObject("SELECT id FROM vacationrental.agencies WHERE name = ?", Long.class, name);
    }

    private List<Map<String, Object>> inbox(Actor actor) throws Exception {
        return JsonPath.read(getWith("/api/mail/inbox", actor.session()).andReturn().getResponse()
                .getContentAsString(), "$");
    }

    private Map<String, Object> inboxItem(Actor actor, String subjectPart) throws Exception {
        return inbox(actor).stream().filter(m -> String.valueOf(m.get("subject")).contains(subjectPart)).findFirst()
                .orElseThrow();
    }

    private void assign(String subjectPart) throws Exception {
        var id = ((Number) inboxItem(anna, subjectPart).get("id")).longValue();
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
    }

    @Test
    void confirmationAfterAssigningTheUnitCreatesABookingToReview() throws Exception {
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        deliver("fewo-cancellation-2025.eml", "2025-09-01T08:00:00Z");
        imports.pollNow(anna.id());

        assertThat(inbox(anna)).hasSize(1);
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("UNASSIGNED_UNIT");
        assertThat(item.get("parsedBy")).isEqualTo("RULE");

        assign("Neue Buchung");
        item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("BOOKING_CREATED");
        var bookingId = ((Number) item.get("linkId")).longValue();
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.agency.name").value("Fewo & Meer"))
                .andExpect(jsonPath("$.externalRef").value("250905"))
                .andExpect(jsonPath("$.expectedRent").value("248.58"))
                .andExpect(jsonPath("$.guest.email").value("erika@example.org"));
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + bookingId, anna.session())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contentType").value("message/rfc822"));

        // Polling again processes nothing twice, and the mailbox stays untouched.
        imports.pollNow(anna.id());
        assertThat(inbox(anna)).hasSize(1);
        var messages = greenMail.getReceivedMessages();
        for (var m : messages) {
            assertThat(m.getFlags().contains(Flags.Flag.SEEN)).isFalse();
        }
        getWith("/api/mail/mailbox", anna.session()).andExpect(jsonPath("$.lastPollAt").exists());
    }

    @Test
    void shortNoticeAttachesAndCancellationProposes() throws Exception {
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        var bookingId = ((Number) inboxItem(anna, "Neue Buchung").get("linkId")).longValue();
        postJson("/api/vacationrental/bookings/" + bookingId + "/confirm", Map.of(), anna.session())
                .andExpect(status().isOk());

        // Mails arriving after the first poll carry the current time as received date.
        deliver("fewo-last-minute.eml", null);
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Last-Minute").get("result")).isEqualTo("ATTACHED");
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.reviewState").value("NONE"))
                .andExpect(jsonPath("$.proposal").doesNotExist());

        deliver("fewo-cancellation-250905.eml", null);
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Stornierung 250905").get("result")).isEqualTo("CHANGE_PROPOSED");
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.reviewState").value("CHANGE"))
                .andExpect(jsonPath("$.proposal.type").value("CANCELLATION"));
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + bookingId, anna.session())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void cancellationOfAnUnconfirmedBookingIsReportedAndEndsTheReviewWhenApplied() throws Exception {
        deliver("fewo-last-minute.eml", "2026-11-06T11:15:00Z");
        imports.pollNow(anna.id());
        assign("Last-Minute");
        var created = inboxItem(anna, "Last-Minute");
        assertThat(created.get("result")).isEqualTo("BOOKING_CREATED");
        var bookingId = ((Number) created.get("linkId")).longValue();

        deliver("fewo-cancellation-250905.eml", null);
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Stornierung 250905").get("result")).isEqualTo("CHANGE_PROPOSED");
        // The booking is still to review, and the cancellation is reported with its mail among the sources.
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.proposal.type").value("CANCELLATION"))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[*].label",
                        org.hamcrest.Matchers.hasItem("Fewo & Meer Stornierung")));

        postJson("/api/vacationrental/bookings/" + bookingId + "/proposal/apply", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reviewState").value("NONE"));
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + bookingId, anna.session())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void cancellationWithoutBookingAndOlderMails() throws Exception {
        deliver("fewo-cancellation-2025.eml", "2025-09-01T08:00:00Z");
        imports.fetchOlder(anna.id(), java.time.LocalDate.of(2025, 1, 1));
        assertThat(inboxItem(anna, "Stornierung 250901").get("result")).isEqualTo("NOT_MATCHED");
        imports.fetchOlder(anna.id(), java.time.LocalDate.of(2025, 1, 1));
        assertThat(inbox(anna)).hasSize(1);
    }

    @Test
    void twoAgenciesMapToTheSameUnit() throws Exception {
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        deliver("holidu-confirmation.eml", "2026-11-02T13:00:00Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        assign("Buchungsmitteilung");
        var holidu = inboxItem(anna, "Buchungsmitteilung");
        assertThat(holidu.get("result")).isEqualTo("BOOKING_CREATED");
        getWith("/api/vacationrental/bookings/" + ((Number) holidu.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.unit.id").value(unit))
                .andExpect(jsonPath("$.agency.name").value("Holidu"))
                .andExpect(jsonPath("$.expectedRent").value("309.08"));
    }

    @Test
    void occupiedPeriodAndUnknownMails() throws Exception {
        booking(anna, unit, "2026-12-24", "2026-12-26");
        deliver("fewo-confirmation-child.eml", "2026-11-07T08:00:00Z");
        deliver("unknown-change.eml", "2026-11-05T17:00:00Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        assertThat(inboxItem(anna, "Neue Buchung").get("result")).isEqualTo("PERIOD_TAKEN");
        var unknown = inboxItem(anna, "Umbuchung");
        assertThat(unknown.get("result")).isEqualTo("NOT_RECOGNISED");
        getWith("/api/mail/inbox/" + ((Number) unknown.get("id")).longValue() + "/text", anna.session())
                .andExpect(jsonPath("$.text").value(containsString("umgebucht")));
    }

    @Test
    void ticketReceiptIsIgnoredAndAStoredOneOnRetry() throws Exception {
        deliver("fewo-ticket-receipt.eml", "2026-11-02T09:00:00Z");
        imports.pollNow(anna.id());
        var item = inboxItem(anna, "Eigentümerblockung");
        assertThat(item.get("result")).isEqualTo("IGNORED");
        assertThat(item.get("kind")).isEqualTo("TICKET_RECEIPT");
        assertThat(item.get("reference")).isEqualTo("ABC-123-45678");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.bookings WHERE unit_id = ?", Integer.class,
                unit)).isZero();

        // Stored as not recognised before receipts were known; the user processes it again.
        byte[] raw;
        try (var in = getClass().getResourceAsStream("/mails/fewo-ticket-receipt.eml")) {
            raw = in.readAllBytes();
        }
        var id = jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "VALUES (?, '<old-receipt@example.org>', now(), ?, 'NOT_RECOGNISED') RETURNING id", Long.class,
                anna.id(), raw);
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("IGNORED"))
                .andExpect(jsonPath("$.kind").value("TICKET_RECEIPT"));
    }

    // --- Extension notices (spec booking-mails, „Extension notices“) ----------------------------------

    /** The invented extension sample with other property, periods and persons; the subject ends with {@code tag}. */
    private void deliverExtension(String tag, String property, String original, String extension, String persons)
            throws Exception {
        String text;
        try (var in = getClass().getResourceAsStream("/mails/fewo-extension.eml")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        text = text.replace("Verlängerungsbuchung\r\n", "Verlängerungsbuchung " + tag + "\r\n")
                .replace("Information über eine Verlängerungsbuchung\n", "Information über eine Verlängerungsbuchung "
                        + tag + "\n")
                .replace("Objekt: Ferienhaus Düne", "Objekt: " + property)
                .replace("14.09.2027 — 17.09.2027", original).replace("12.09.2027 — 14.09.2027", extension)
                .replace("(3) Erwachsene/ (0) Kinder", persons);
        var message = new MimeMessage(Session.getInstance(new Properties()),
                new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        message.setHeader("Message-ID", "<extension-" + tag + "@example.org>");
        greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                .appendMessage(message, new Flags(), Date.from(clock.instant()));
    }

    @Test
    void extensionProposesTheLongerStayWithMoreGuests() throws Exception {
        var booking = booking(anna, unit, "2027-09-14", "2027-09-17");
        deliverExtension("E1", "Ferienhaus Düne", "14.09.2027 — 17.09.2027", "12.09.2027 — 14.09.2027",
                "(3) Erwachsene/ (0) Kinder");
        imports.pollNow(anna.id());
        // An unknown property waits for a unit like other Fewo & Meer mails.
        assertThat(inboxItem(anna, "E1").get("result")).isEqualTo("UNASSIGNED_UNIT");
        assign("E1");
        var item = inboxItem(anna, "E1");
        assertThat(item.get("result")).isEqualTo("CHANGE_PROPOSED");
        assertThat(item.get("kind")).isEqualTo("EXTENSION");
        assertThat(((Number) item.get("linkId")).longValue()).isEqualTo(booking);
        getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(jsonPath("$.reviewState").value("CHANGE"))
                .andExpect(jsonPath("$.proposal.arrival").value("2027-09-12"))
                .andExpect(jsonPath("$.proposal.departure").doesNotExist())
                .andExpect(jsonPath("$.proposal.adults").value(3));
    }

    @Test
    void extensionCompletesABookingNotReviewedYet() throws Exception {
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        var booking = bookingOf("Neue Buchung");
        var property = jdbc.queryForObject("SELECT property_name FROM vacationrental.unit_mappings WHERE unit_id = ? "
                + "LIMIT 1", String.class, unit);
        deliverExtension("E2", property, "06.11.2026 — 08.11.2026", "08.11.2026 — 09.11.2026",
                "(4) Erwachsene/ (0) Kinder");
        imports.pollNow(anna.id());
        var item = inboxItem(anna, "E2");
        assertThat(item.get("result")).isEqualTo("ATTACHED");
        assertThat(item.get("resultDetail")).isEqualTo("Angaben ergänzt");
        getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.departure").value("2026-11-09"));
    }

    @Test
    void extensionAlreadyRecordedOrWithoutBooking() throws Exception {
        var booking = booking(anna, unit, "2027-03-01", "2027-03-05");
        deliverExtension("E3", "Ferienhaus Düne", "01.03.2027 — 04.03.2027", "04.03.2027 — 05.03.2027",
                "(2) Erwachsene/ (0) Kinder");
        deliverExtension("E4", "Ferienhaus Düne", "01.05.2027 — 03.05.2027", "03.05.2027 — 04.05.2027",
                "(2) Erwachsene/ (0) Kinder");
        imports.pollNow(anna.id());
        assign("E3");
        var recorded = inboxItem(anna, "E3");
        assertThat(recorded.get("result")).isEqualTo("ATTACHED");
        assertThat(recorded.get("resultDetail")).isEqualTo("Verlängerung bereits erfasst");
        assertThat(((Number) recorded.get("linkId")).longValue()).isEqualTo(booking);
        // The unit mapping now exists: the second mail is processed again on retry.
        var missing = inboxItem(anna, "E4");
        postJson("/api/mail/inbox/" + ((Number) missing.get("id")).longValue() + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_MATCHED"))
                .andExpect(jsonPath("$.resultDetail").value("Keine Buchung vom 01.05.2027 bis 03.05.2027"));
    }

    @Test
    void storedExtensionIsRecognisedOnRetry() throws Exception {
        var booking = booking(anna, unit, "2027-09-14", "2027-09-17");
        // Stored as not recognised before extension notices were known ...
        byte[] raw;
        try (var in = getClass().getResourceAsStream("/mails/fewo-extension.eml")) {
            raw = in.readAllBytes();
        }
        var id = jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "VALUES (?, '<old-extension@example.org>', now(), ?, 'NOT_RECOGNISED') RETURNING id", Long.class,
                anna.id(), raw);
        // ... and recognised once the user retries it.
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("UNASSIGNED_UNIT"))
                .andExpect(jsonPath("$.kind").value("EXTENSION"));
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(jsonPath("$.reviewState").value("CHANGE"))
                .andExpect(jsonPath("$.proposal.arrival").value("2027-09-12"));
    }

    @Test
    void manualBookingIsMatchedByNumberAndDiscardMarksTheMail() throws Exception {
        var body = stay(unit, "2026-11-06", "2026-11-08");
        body.put("externalRef", "250905");
        body.put("adults", 4);
        body.put("expectedRent", "248.58");
        var manual = id(book(anna, body));
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        imports.pollNow(anna.id());
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("CHANGE_PROPOSED");
        assertThat(((Number) item.get("linkId")).longValue()).isEqualTo(manual);
        getWith("/api/vacationrental/bookings/" + manual, anna.session())
                .andExpect(jsonPath("$.agency.name").value("Fewo & Meer"))
                .andExpect(jsonPath("$.proposal.guest.name").value("Erika Musterfrau"))
                .andExpect(jsonPath("$.proposal.arrival").doesNotExist());
    }

    @Test
    void discardingAnImportedBookingMarksItsMail() throws Exception {
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        var bookingId = ((Number) inboxItem(anna, "Neue Buchung").get("linkId")).longValue();
        postJson("/api/vacationrental/bookings/" + bookingId + "/discard", Map.of(), anna.session())
                .andExpect(status().isNoContent());
        assertThat(inboxItem(anna, "Neue Buchung").get("result")).isEqualTo("DISCARDED");
    }

    @Test
    void mailFromADisabledPeriodIsHandledOnRetry() throws Exception {
        // A mail stored while the module was disabled (see DisabledModuleMailTest) ...
        byte[] raw;
        try (var in = getClass().getResourceAsStream("/mails/fewo-confirmation-adults.eml")) {
            raw = in.readAllBytes();
        }
        var id = jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "VALUES (?, '<y@example.org>', now(), ?, 'NOT_RECOGNISED') RETURNING id", Long.class, anna.id(), raw);
        // ... is handled once the module is enabled and the user retries.
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("UNASSIGNED_UNIT"));
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        getWith("/api/mail/inbox", anna.session()).andExpect(jsonPath("$[0].result").value("BOOKING_CREATED"));
    }

    @Test
    void forwardedConfirmationWithSpacesAroundTheTabCreatesABooking() throws Exception {
        deliver("fewo-forward-apple-plain.eml", "2026-11-09T08:12:00Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(item.get("parsedBy")).isEqualTo("RULE");
        getWith("/api/vacationrental/bookings/" + ((Number) item.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.externalRef").value("250906"))
                .andExpect(jsonPath("$.arrival").value("2026-11-09"))
                .andExpect(jsonPath("$.departure").value("2026-11-13"))
                .andExpect(jsonPath("$.guest.name").value("Johanna Beispiel"))
                .andExpect(jsonPath("$.expectedRent").value("387.45"));
    }

    @Test
    void unrecognisedForwardIsRecognisedOnRetry() throws Exception {
        // Stored as not recognised before the separator fix ...
        byte[] raw;
        try (var in = getClass().getResourceAsStream("/mails/fewo-forward-apple-html.eml")) {
            raw = in.readAllBytes();
        }
        var id = jdbc.queryForObject("INSERT INTO core.mails (owner_user_id, message_id, received_at, raw, result) "
                + "VALUES (?, '<fwd@example.org>', now(), ?, 'NOT_RECOGNISED') RETURNING id", Long.class, anna.id(),
                raw);
        // ... and read once the user retries it.
        postJson("/api/mail/inbox/" + id + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("UNASSIGNED_UNIT"));
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("BOOKING_CREATED");
        getWith("/api/vacationrental/bookings/" + ((Number) item.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.externalRef").value("250906"));
    }

    @Test
    void fewoScoutConfirmationWithoutSignatureCreatesABooking() throws Exception {
        deliver("fewoscout-confirmation.eml", "2026-11-02T08:44:46Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(item.get("parsedBy")).isEqualTo("RULE");
        getWith("/api/vacationrental/bookings/" + ((Number) item.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.agency.name").value("Fewo & Meer"))
                .andExpect(jsonPath("$.externalRef").value("250902"))
                .andExpect(jsonPath("$.adults").value(2))
                .andExpect(jsonPath("$.expectedRent").value("432.10"));
    }

    @Test
    void vvvBookingGetsItsOwnAgencyNextToHolidu() throws Exception {
        deliver("holidu-confirmation.eml", "2026-11-02T13:00:00Z");
        deliver("ds-vvv-confirmation.eml", "2026-11-02T13:10:00Z");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung 3e12f345gh");
        // The Holidu mapping does not cover VVV: its first mail asks for the unit once.
        var vvv = inboxItem(anna, "Buchungsmitteilung 3e98f765kl");
        assertThat(vvv.get("result")).isEqualTo("UNASSIGNED_UNIT");
        assign("Buchungsmitteilung 3e98f765kl");
        vvv = inboxItem(anna, "Buchungsmitteilung 3e98f765kl");
        assertThat(vvv.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(vvv.get("source")).isEqualTo("VVV-Stadt- und Citymarketing Talstadt e.V.");
        getWith("/api/vacationrental/bookings/" + ((Number) vvv.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.agency.name").value("VVV-Stadt- und Citymarketing Talstadt e.V."))
                .andExpect(jsonPath("$.adults").value(2))
                .andExpect(jsonPath("$.children").value(2))
                .andExpect(jsonPath("$.expectedRent").value("447.00"));
        var holidu = inboxItem(anna, "Buchungsmitteilung 3e12f345gh");
        getWith("/api/vacationrental/bookings/" + ((Number) holidu.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.agency.name").value("Holidu"));
    }

    @Test
    void blockNoticeIsAttachedToTheStayWithTheSamePeriod() throws Exception {
        deliver("ds-vvv-confirmation.eml", "2026-11-02T10:50:00Z");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung 3e98f765kl");
        var bookingId = ((Number) inboxItem(anna, "Buchungsmitteilung 3e98f765kl").get("linkId")).longValue();

        deliver("fewo-block-notice.eml", null);
        imports.pollNow(anna.id());
        assign("Neue Blockung");
        var item = inboxItem(anna, "Neue Blockung");
        assertThat(item.get("result")).isEqualTo("ATTACHED");
        assertThat(item.get("kind")).isEqualTo("BLOCK_NOTICE");
        assertThat(item.get("resultDetail")).isEqualTo("Blockung durch VVV Talstadt");
        assertThat(((Number) item.get("linkId")).longValue()).isEqualTo(bookingId);
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.externalRef").value("3e98f765kl"))
                .andExpect(jsonPath("$.agency.name").value("VVV-Stadt- und Citymarketing Talstadt e.V."))
                .andExpect(jsonPath("$.proposal").doesNotExist());
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + bookingId, anna.session())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fileName", hasItem(containsString("Blockung 250904"))));
        getWith("/api/vacationrental/bookings", anna.session()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void blockNoticeBeforeTheBookingMailIsMatchedOnRetry() throws Exception {
        deliver("fewo-block-notice.eml", "2026-11-02T10:55:00Z");
        imports.pollNow(anna.id());
        assign("Neue Blockung");
        var item = inboxItem(anna, "Neue Blockung");
        assertThat(item.get("result")).isEqualTo("NOT_MATCHED");
        assertThat(item.get("resultDetail")).isEqualTo("Keine Buchung vom 15.05.2027 bis 18.05.2027");
        getWith("/api/vacationrental/bookings", anna.session()).andExpect(jsonPath("$.totalItems").value(0));

        var bookingId = booking(anna, unit, "2027-05-15", "2027-05-18");
        postJson("/api/mail/inbox/" + ((Number) item.get("id")).longValue() + "/retry", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ATTACHED"));
        assertThat(((Number) inboxItem(anna, "Neue Blockung").get("linkId")).longValue()).isEqualTo(bookingId);
    }

    @Test
    void blockNoticeWithADifferentPeriodIsNotMatched() throws Exception {
        booking(anna, unit, "2027-05-15", "2027-05-19");
        deliver("fewo-block-notice.eml", "2026-11-02T10:55:00Z");
        imports.pollNow(anna.id());
        assign("Neue Blockung");
        assertThat(inboxItem(anna, "Neue Blockung").get("result")).isEqualTo("NOT_MATCHED");
        getWith("/api/vacationrental/bookings", anna.session()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void ownerBlockCreatesAnOwnerUseWithoutReview() throws Exception {
        deliver("fewo-owner-block.eml", null);
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Eigentümerblockung").get("result")).isEqualTo("UNASSIGNED_UNIT");
        assign("Eigentümerblockung");
        var item = inboxItem(anna, "Eigentümerblockung");
        assertThat(item.get("result")).isEqualTo("OWNER_USE_CREATED");
        assertThat(item.get("kind")).isEqualTo("OWNER_BLOCK");
        var id = ((Number) item.get("linkId")).longValue();
        getWith("/api/vacationrental/bookings/" + id, anna.session())
                .andExpect(jsonPath("$.kind").value("OWNER"))
                .andExpect(jsonPath("$.externalRef").value("275001"))
                .andExpect(jsonPath("$.arrival").value("2027-06-09"))
                .andExpect(jsonPath("$.departure").value("2027-06-14"))
                .andExpect(jsonPath("$.reviewState").value("NONE"))
                .andExpect(jsonPath("$.adults").value(0))
                .andExpect(jsonPath("$.agency.name").value("Fewo & Meer"));
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + id, anna.session())
                .andExpect(jsonPath("$[*].fileName", hasItem(containsString("Eigentümerblockung 275001"))));

        // The same number with another period moves the owner use directly.
        var moved = new String(getClass().getResourceAsStream("/mails/fewo-owner-block.eml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)
                .replace("vom 09.06.2027", "vom 10.06.2027").replace("fewo-owner-block-275001", "fewo-owner-block-2");
        greenMail.getManagers().getImapHostManager().getInbox(mailUser).appendMessage(
                new MimeMessage(Session.getInstance(new Properties()),
                        new java.io.ByteArrayInputStream(moved.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                new Flags(), Date.from(clock.instant()));
        imports.pollNow(anna.id());
        getWith("/api/vacationrental/bookings/" + id, anna.session())
                .andExpect(jsonPath("$.arrival").value("2027-06-10"));
        getWith("/api/vacationrental/bookings", anna.session()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void ownerBlockTakesOverAnOwnerUseRecordedByHand() throws Exception {
        var body = new HashMap<String, Object>(Map.of("unitId", unit, "arrival", "2027-06-09",
                "departure", "2027-06-14", "kind", "OWNER"));
        var id = ((Number) JsonPath.read(postJson("/api/vacationrental/bookings", body, anna.session())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        deliver("fewo-owner-block.eml", null);
        imports.pollNow(anna.id());
        assign("Eigentümerblockung");
        var item = inboxItem(anna, "Eigentümerblockung");
        assertThat(item.get("result")).isEqualTo("ATTACHED");
        assertThat(((Number) item.get("linkId")).longValue()).isEqualTo(id);
        getWith("/api/vacationrental/bookings/" + id, anna.session())
                .andExpect(jsonPath("$.externalRef").value("275001"))
                .andExpect(jsonPath("$.agency.name").value("Fewo & Meer"));
        getWith("/api/vacationrental/bookings", anna.session()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void ownerBlockForAnOccupiedPeriodIsNotCreated() throws Exception {
        booking(anna, unit, "2027-06-12", "2027-06-15");
        deliver("fewo-owner-block.eml", null);
        imports.pollNow(anna.id());
        assign("Eigentümerblockung");
        assertThat(inboxItem(anna, "Eigentümerblockung").get("result")).isEqualTo("PERIOD_TAKEN");
        getWith("/api/vacationrental/bookings", anna.session()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void unknownIntermediaryCreatesAnAgency() throws Exception {
        var name = VacationRentalTest.unique("Ferienbüro Süd");
        deliverDs(name, "ref-" + System.nanoTime(), "01.06.2027", "04.06.2027");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung");
        var agencyId = agencyIdByName(name);
        getWith("/api/vacationrental/bookings/" + bookingOf("Buchungsmitteilung"), anna.session())
                .andExpect(jsonPath("$.agency.id").value(agencyId.intValue()))
                .andExpect(jsonPath("$.agency.name").value(name));
    }

    @Test
    void renamedAgencyKeepsMatchingMailsAndUnitMappings() throws Exception {
        var longName = VacationRentalTest.unique("Stadtmarketing Musterstadt e.V.");
        var first = "a-" + System.nanoTime();
        deliverDs(longName, first, "01.07.2027", "04.07.2027");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung " + first);
        var agencyId = agencyIdByName(longName);
        var version = jdbc.queryForObject("SELECT version FROM vacationrental.agencies WHERE id = ?", Long.class,
                agencyId);
        var shortName = VacationRentalTest.unique("Stadtmarketing");
        mvc.perform(put("/api/vacationrental/agencies/" + agencyId).with(csrf()).session(anna.session())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", shortName, "version", version))))
                .andExpect(status().isOk());

        // The next mail still names the long form: same agency, and the unit mapping applies without asking.
        var second = "b-" + System.nanoTime();
        deliverDs(longName, second, "10.07.2027", "12.07.2027");
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Buchungsmitteilung " + second).get("result")).isEqualTo("BOOKING_CREATED");
        getWith("/api/vacationrental/bookings/" + bookingOf("Buchungsmitteilung " + second), anna.session())
                .andExpect(jsonPath("$.agency.id").value(agencyId.intValue()))
                .andExpect(jsonPath("$.agency.name").value(shortName));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.agencies WHERE lower(name) = lower(?)",
                Long.class, longName)).isZero();
    }

    @Test
    void mailNamingAnAddedMailNameMatchesTheBookingByNumber() throws Exception {
        var name = VacationRentalTest.unique("Ferienvermittlung Ost");
        var ref = "c-" + System.nanoTime();
        deliverDs(name, ref, "01.08.2027", "04.08.2027");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung " + ref);
        var bookingId = bookingOf("Buchungsmitteilung " + ref);
        var agencyId = agencyIdByName(name);
        var otherSpelling = name.toUpperCase() + " GmbH";
        postJson("/api/vacationrental/agencies/" + agencyId + "/aliases", Map.of("alias", otherSpelling),
                anna.session()).andExpect(status().isOk());

        // Same booking under the other spelling: found by agency identity and number, nothing new is created.
        deliverDs(otherSpelling, ref, "01.08.2027", "04.08.2027", "again-" + ref);
        imports.pollNow(anna.id());
        var again = inbox(anna).stream().filter(m -> ((Number) m.get("linkId")) != null
                && ((Number) m.get("linkId")).longValue() == bookingId && "ATTACHED".equals(m.get("result")))
                .findFirst();
        assertThat(again).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.agencies WHERE lower(name) = lower(?)",
                Long.class, otherSpelling)).isZero();
    }

    @Test
    void forwardedMailsWorkLikeOriginals() throws Exception {
        deliver("fewo-forward-inline-html.eml", "2026-11-06T11:30:00Z");
        deliver("holidu-forward-attachment.eml", "2026-11-02T13:30:00Z");
        imports.pollNow(anna.id());
        assign("WG: Neue Buchung");
        assign("Buchungsmitteilung 3e12f345gh");
        var fewo = inboxItem(anna, "WG: Neue Buchung");
        assertThat(fewo.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(fewo.get("source")).isEqualTo("Fewo & Meer");
        assertThat(inboxItem(anna, "Buchungsmitteilung 3e12f345gh").get("result")).isEqualTo("BOOKING_CREATED");
        var bookingId = ((Number) fewo.get("linkId")).longValue();
        postJson("/api/vacationrental/bookings/" + bookingId + "/confirm", Map.of(), anna.session())
                .andExpect(status().isOk());

        deliver("fewo-forward-quoted-cancellation.eml", null);
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Fwd: Stornierung").get("result")).isEqualTo("CHANGE_PROPOSED");
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.proposal.type").value("CANCELLATION"));
    }
}
