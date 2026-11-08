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
        unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
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
                .andExpect(jsonPath("$.agency").value("Fewo & Meer"))
                .andExpect(jsonPath("$.externalRef").value("275505"))
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

        deliver("fewo-cancellation-275505.eml", null);
        imports.pollNow(anna.id());
        assertThat(inboxItem(anna, "Stornierung 275505").get("result")).isEqualTo("CHANGE_PROPOSED");
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.reviewState").value("CHANGE"))
                .andExpect(jsonPath("$.proposal.type").value("CANCELLATION"));
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + bookingId, anna.session())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void cancellationWithoutBookingAndOlderMails() throws Exception {
        deliver("fewo-cancellation-2025.eml", "2025-09-01T08:00:00Z");
        imports.fetchOlder(anna.id(), java.time.LocalDate.of(2025, 1, 1));
        assertThat(inboxItem(anna, "Stornierung 256456").get("result")).isEqualTo("NOT_MATCHED");
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
                .andExpect(jsonPath("$.agency").value("Holidu"))
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
    void manualBookingIsMatchedByNumberAndDiscardMarksTheMail() throws Exception {
        var body = stay(unit, "2026-11-06", "2026-11-08");
        body.put("externalRef", "275505");
        body.put("adults", 4);
        body.put("expectedRent", "248.58");
        var manual = id(book(anna, body));
        deliver("fewo-confirmation-adults.eml", "2026-11-06T11:15:00Z");
        imports.pollNow(anna.id());
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("CHANGE_PROPOSED");
        assertThat(((Number) item.get("linkId")).longValue()).isEqualTo(manual);
        getWith("/api/vacationrental/bookings/" + manual, anna.session())
                .andExpect(jsonPath("$.agency").value("Fewo & Meer"))
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
    void fewoScoutConfirmationWithoutSignatureCreatesABooking() throws Exception {
        deliver("fewoscout-confirmation.eml", "2026-11-02T08:44:46Z");
        imports.pollNow(anna.id());
        assign("Neue Buchung");
        var item = inboxItem(anna, "Neue Buchung");
        assertThat(item.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(item.get("parsedBy")).isEqualTo("RULE");
        getWith("/api/vacationrental/bookings/" + ((Number) item.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.agency").value("Fewo & Meer"))
                .andExpect(jsonPath("$.externalRef").value("274907"))
                .andExpect(jsonPath("$.adults").value(2))
                .andExpect(jsonPath("$.expectedRent").value("454.32"));
    }

    @Test
    void vvvBookingGetsItsOwnAgencyNextToHolidu() throws Exception {
        deliver("holidu-confirmation.eml", "2026-11-02T13:00:00Z");
        deliver("ds-vvv-confirmation.eml", "2026-11-02T13:10:00Z");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung 6a31h227wy");
        // The Holidu mapping does not cover VVV: its first mail asks for the unit once.
        var vvv = inboxItem(anna, "Buchungsmitteilung 6a29b55l9j");
        assertThat(vvv.get("result")).isEqualTo("UNASSIGNED_UNIT");
        assign("Buchungsmitteilung 6a29b55l9j");
        vvv = inboxItem(anna, "Buchungsmitteilung 6a29b55l9j");
        assertThat(vvv.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(vvv.get("source")).isEqualTo("VVV-Stadt- und Citymarketing Nordhorn e.V.");
        getWith("/api/vacationrental/bookings/" + ((Number) vvv.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.reviewState").value("NEW"))
                .andExpect(jsonPath("$.agency").value("VVV-Stadt- und Citymarketing Nordhorn e.V."))
                .andExpect(jsonPath("$.adults").value(2))
                .andExpect(jsonPath("$.children").value(2))
                .andExpect(jsonPath("$.expectedRent").value("447.00"));
        var holidu = inboxItem(anna, "Buchungsmitteilung 6a31h227wy");
        getWith("/api/vacationrental/bookings/" + ((Number) holidu.get("linkId")).longValue(), anna.session())
                .andExpect(jsonPath("$.agency").value("Holidu"));
    }

    @Test
    void blockNoticeIsAttachedToTheStayWithTheSamePeriod() throws Exception {
        deliver("ds-vvv-confirmation.eml", "2026-11-02T10:50:00Z");
        imports.pollNow(anna.id());
        assign("Buchungsmitteilung 6a29b55l9j");
        var bookingId = ((Number) inboxItem(anna, "Buchungsmitteilung 6a29b55l9j").get("linkId")).longValue();

        deliver("fewo-block-notice.eml", null);
        imports.pollNow(anna.id());
        assign("Neue Blockung");
        var item = inboxItem(anna, "Neue Blockung");
        assertThat(item.get("result")).isEqualTo("ATTACHED");
        assertThat(item.get("kind")).isEqualTo("BLOCK_NOTICE");
        assertThat(item.get("resultDetail")).isEqualTo("Blockung durch VVV Nordhorn");
        assertThat(((Number) item.get("linkId")).longValue()).isEqualTo(bookingId);
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.externalRef").value("6a29b55l9j"))
                .andExpect(jsonPath("$.agency").value("VVV-Stadt- und Citymarketing Nordhorn e.V."))
                .andExpect(jsonPath("$.proposal").doesNotExist());
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + bookingId, anna.session())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].fileName", hasItem(containsString("Blockung 275206"))));
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
    void forwardedMailsWorkLikeOriginals() throws Exception {
        deliver("fewo-forward-inline-html.eml", "2026-11-06T11:30:00Z");
        deliver("holidu-forward-attachment.eml", "2026-11-02T13:30:00Z");
        imports.pollNow(anna.id());
        assign("WG: Neue Buchung");
        assign("Buchungsmitteilung 6a31h227wy");
        var fewo = inboxItem(anna, "WG: Neue Buchung");
        assertThat(fewo.get("result")).isEqualTo("BOOKING_CREATED");
        assertThat(fewo.get("source")).isEqualTo("Fewo & Meer");
        assertThat(inboxItem(anna, "Buchungsmitteilung 6a31h227wy").get("result")).isEqualTo("BOOKING_CREATED");
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
