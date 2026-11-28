package app.logis.modules.vacationrental;

import app.logis.core.notifications.TestChannels;
import app.logis.core.users.Role;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications about bookings and changes from mails that wait for review (spec bookings, „Notify about bookings
 * awaiting review“). Sample mails with invented values only.
 */
@Import(TestChannels.class)
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class BookingNotificationsTest extends VacationRentalTest {

    private static final String[] TYPES = {"vacationrental.bookingReview", "vacationrental.changeReview"};

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    @Autowired
    app.logis.core.mail.Mails imports;
    @Autowired
    TestChannels.Recording recording;
    @Autowired
    TestChannels.LongRecording longRecording;

    private GreenMailUser mailUser;
    private Actor anna;
    private Long unit;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        anna = actor();
        unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var body = Map.of("host", "localhost", "port", greenMail.getImap().getPort(), "username", "anna",
                "password", "geheim", "folder", "INBOX", "useTls", false, "cutoffDate", "2026-01-01", "enabled", true);
        mvc.perform(put("/api/mail/mailbox").with(csrf()).session(anna.session()).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
        TestChannels.switchOn(jdbc, anna.id(), TYPES);
    }

    /** Delivers a sample mail as received now, so it counts as new. */
    private void deliver(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/mails/" + name)) {
            var message = new MimeMessage(Session.getInstance(new Properties()), in);
            greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                    .appendMessage(message, new Flags(), Date.from(clock.instant()));
        }
    }

    private Map<String, Object> inboxItem(String subjectPart) throws Exception {
        List<Map<String, Object>> inbox = JsonPath.read(getWith("/api/mail/inbox", anna.session()).andReturn()
                .getResponse().getContentAsString(), "$");
        return inbox.stream().filter(m -> String.valueOf(m.get("subject")).contains(subjectPart)).findFirst()
                .orElseThrow();
    }

    /** The first confirmation waits for a unit; assigning it teaches Logis the property for later mails. */
    private Long firstBookingAssignedAndConfirmed() throws Exception {
        deliver("fewo-confirmation-adults.eml");
        imports.pollNow(anna.id());
        var mailId = ((Number) inboxItem("Neue Buchung").get("id")).longValue();
        postJson("/api/vacationrental/mail/" + mailId + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        var bookingId = ((Number) inboxItem("Neue Buchung").get("linkId")).longValue();
        postJson("/api/vacationrental/bookings/" + bookingId + "/confirm", Map.of(), anna.session())
                .andExpect(status().isOk());
        return bookingId;
    }

    @Test
    void aNewBookingTellsThoseWhoCanConfirmItAndOnlyTheLongFormNamesTheGuest() throws Exception {
        var carla = actor();
        var bert = actor();
        var admin = createUser(Role.ADMIN, true);
        share(anna, "unit", unit, carla, "EDITOR");
        share(anna, "unit", unit, bert, "VIEWER");
        for (var id : List.of(carla.id(), bert.id(), admin.id())) {
            TestChannels.switchOn(jdbc, id, TYPES);
        }
        // The booking Anna created by assigning the unit herself raises nothing.
        firstBookingAssignedAndConfirmed();
        recording.await(anna.id(), 0);

        deliver("fewo-confirmation-child.eml");
        imports.pollNow(anna.id());

        var bookingId = jdbc.queryForObject("SELECT id FROM vacationrental.bookings WHERE unit_id = ? "
                + "AND external_ref = '250903'", Long.class, unit);
        var push = recording.await(anna.id(), 1).get(0);
        assertThat(push.title()).isEqualTo("Neue Buchung zu prüfen");
        assertThat(push.text()).isEqualTo("Haus Waldeck, 23.12.2026 – 27.12.2026 (4 Nächte), über Fewo & Meer");
        assertThat(push.link()).isEqualTo("https://logis.example.org/ferienvermietung/buchungen/" + bookingId);
        var mail = longRecording.await(anna.id(), 1).get(0).text();
        assertThat(mail).contains("Objekt: Haus Waldeck").contains("Zeitraum: 23.12.2026 – 27.12.2026 (4 Nächte)")
                .contains("Vermittler: Fewo & Meer").contains("Gast: Jonas Beispiel").contains("Personen: ")
                .contains("Erwartete Miete: ").contains("Buchungsnummer: 250903");
        // Nothing of the guest, the amount or the booking number leaves in the short form.
        var rent = jdbc.queryForObject("SELECT expected_rent FROM vacationrental.bookings WHERE id = ?",
                java.math.BigDecimal.class, bookingId);
        assertThat(push.title() + push.text()).doesNotContain("Jonas").doesNotContain("Beispiel")
                .doesNotContain("250903").doesNotContain(BookingNotifications.euro(rent))
                .doesNotContain(rent.toPlainString()).doesNotContain("€");

        // The editor of the unit can confirm, the viewer cannot, and the admin has no role at all.
        recording.await(carla.id(), 1);
        recording.await(bert.id(), 0);
        recording.await(admin.id(), 0);
    }

    @Test
    void aProposedCancellationSaysSo() throws Exception {
        var bookingId = firstBookingAssignedAndConfirmed();

        deliver("fewo-cancellation-250905.eml");
        imports.pollNow(anna.id());

        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.proposal.type").value("CANCELLATION"));
        var push = recording.await(anna.id(), 1).get(0);
        assertThat(push.title()).isEqualTo("Stornierung zu prüfen");
        assertThat(push.text()).startsWith("Haus Waldeck, 06.11.2026 – 08.11.2026")
                .endsWith(": Stornierung vorgeschlagen").doesNotContain("Musterfrau").doesNotContain("250905");
        assertThat(push.link()).endsWith("/ferienvermietung/buchungen/" + bookingId);
        assertThat(longRecording.await(anna.id(), 1).get(0).text()).contains("zu stornieren")
                .contains("Gast: Erika Musterfrau").contains("Buchungsnummer: 250905");
    }

    @Test
    void olderMailsAndUsersWhoDidNotSwitchItOnGetNothing() throws Exception {
        var dieter = actor();
        share(anna, "unit", unit, dieter, "EDITOR");
        firstBookingAssignedAndConfirmed();
        // Fetched as older mails: forty bookings would otherwise mean forty notifications.
        jdbc.update("UPDATE core.mailboxes SET cutoff_date = ? WHERE owner_user_id = ?",
                java.time.LocalDate.now().plusDays(1), anna.id());
        deliver("fewo-confirmation-child.eml");
        imports.fetchOlder(anna.id(), java.time.LocalDate.of(2026, 1, 1));

        assertThat(jdbc.queryForObject("SELECT review_state FROM vacationrental.bookings WHERE unit_id = ? "
                + "AND external_ref = '250903'", String.class, unit)).isEqualTo("NEW");
        recording.await(anna.id(), 0);
        recording.await(dieter.id(), 0);
    }

    @Test
    void aProposedChangeNamesTheNewValuesOnlyInTheLongForm() {
        var booking = new Booking(Instant.parse("2026-11-01T10:00:00Z"));
        booking.apply(unit, java.time.LocalDate.of(2027, 7, 12), java.time.LocalDate.of(2027, 7, 19), "TEST-4711", 2,
                1, new java.math.BigDecimal("1234.50"));
        booking.setGuest(new Guest("Erika Musterfrau", null, null, null, null, null, "erika@example.org"));
        var summary = new app.logis.core.realestate.UnitSummary(unit, "Whg. 2", 1L, "Haus Talblick", true,
                app.logis.core.realestate.UnitUsage.VACATION_RENTAL, false, false);
        var proposal = new BookingProposal(BookingProposal.Type.CHANGE, null, java.time.LocalDate.of(2027, 7, 21), 3,
                null, null, new java.math.BigDecimal("1400.00"), null, 5L);
        booking.setProposal(proposal);

        var note = BookingNotifications.proposal(booking, summary, "Fewo & Meer");

        assertThat(note.title()).isEqualTo("Änderung zu prüfen");
        assertThat(note.shortText()).isEqualTo(
                "Haus Talblick, Whg. 2, 12.07.2027 – 19.07.2027 (7 Nächte), über Fewo & Meer: Änderung vorgeschlagen");
        assertThat(note.longText()).contains("Neuer Zeitraum: 12.07.2027 – 21.07.2027 (9 Nächte)")
                .contains("Neue Personenzahl: 3 Erwachsene, 1 Kind").contains("Neue erwartete Miete: 1.400,00 €")
                .contains("Erwartete Miete: 1.234,50 €").contains("Gast: Erika Musterfrau")
                .contains("Buchungsnummer: TEST-4711");
        assertThat(note.title() + note.shortText()).doesNotContain("Erika").doesNotContain("1.234")
                .doesNotContain("1.400").doesNotContain("TEST-4711").doesNotContain("example.org");
    }
}
