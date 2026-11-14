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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Imported mails become sources of their booking, with the location of every value (booking-sources spec). */
class MailSourcesTest extends VacationRentalTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.IMAP);

    @Autowired
    app.logis.core.mail.Mails imports;

    private GreenMailUser mailUser;
    private Actor anna;
    private Long property;
    private Long unit;

    @BeforeEach
    void setUp() throws Exception {
        mailUser = greenMail.setUser("anna@example.org", "anna", "geheim");
        anna = actor();
        property = singleUnitProperty(anna, "Haus Seeufer");
        unit = unitIds(property).getFirst();
        var body = new HashMap<String, Object>(Map.of("host", "localhost", "port", greenMail.getImap().getPort(),
                "username", "anna", "password", "geheim", "folder", "INBOX", "useTls", false,
                "cutoffDate", "2026-11-01", "enabled", true));
        mvc.perform(put("/api/mail/mailbox").with(csrf()).session(anna.session()).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
    }

    /** A test mail with some text replaced and its own message id, delivered now. */
    private void deliver(String name, String messageId, String... replacements) throws Exception {
        String text;
        try (var in = getClass().getResourceAsStream("/mails/" + name)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (int i = 0; i < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        var message = new MimeMessage(Session.getInstance(new Properties()),
                new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        if (messageId != null) {
            message.setHeader("Message-ID", "<" + messageId + "@example.org>");
        }
        greenMail.getManagers().getImapHostManager().getInbox(mailUser)
                .appendMessage(message, new Flags(), Date.from(clock.instant()));
        imports.pollNow(anna.id());
    }

    private Map<String, Object> inboxItem(String subjectPart, String result) throws Exception {
        List<Map<String, Object>> inbox = JsonPath.read(getWith("/api/mail/inbox", anna.session()).andReturn()
                .getResponse().getContentAsString(), "$");
        return inbox.stream().filter(m -> String.valueOf(m.get("subject")).contains(subjectPart)
                && (result == null || result.equals(m.get("result")))).findFirst().orElseThrow();
    }

    /** The first confirmation of booking 250905, assigned to the unit; returns the booking. */
    private Long imported() throws Exception {
        deliver("fewo-confirmation-adults.eml", null);
        var id = ((Number) inboxItem("Neue Buchung", null).get("id")).longValue();
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        return ((Number) inboxItem("Neue Buchung", "BOOKING_CREATED").get("linkId")).longValue();
    }

    private List<Map<String, Object>> sources(Long bookingId) {
        return jdbc.queryForList("SELECT id, label, subject FROM vacationrental.booking_sources WHERE booking_id = ? "
                + "ORDER BY id", bookingId);
    }

    /** The source and the text at the location of a field, or {@code null} without a location. */
    private Map<String, Object> at(Long bookingId, String scope, String field) {
        var rows = jdbc.queryForList("SELECT f.source_id, f.source_value, CASE f.part WHEN 'SUBJECT' "
                + "THEN substring(s.subject FROM f.start_pos + 1 FOR f.end_pos - f.start_pos) "
                + "ELSE substring(s.text FROM f.start_pos + 1 FOR f.end_pos - f.start_pos) END AS written "
                + "FROM vacationrental.booking_field_sources f JOIN vacationrental.booking_sources s "
                + "ON s.id = f.source_id WHERE f.booking_id = ? AND f.scope = ? AND f.field = ?", bookingId, scope,
                field);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private long sourceOf(Long bookingId, String scope, String field) {
        return ((Number) at(bookingId, scope, field).get("source_id")).longValue();
    }

    private long sourceId(Map<String, Object> source) {
        return ((Number) source.get("id")).longValue();
    }

    @Test
    void confirmationBecomesASourceWithLocations() throws Exception {
        var booking = imported();
        var sources = sources(booking);
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().get("label")).isEqualTo("Fewo & Meer Buchungsbestätigung");
        assertThat(sources.getFirst().get("subject")).isEqualTo("Neue Buchung für Haus Seeufer - Nordhorn");
        assertThat(at(booking, "CURRENT", "arrival")).containsEntry("written", "06.11.2026")
                .containsEntry("source_value", "2026-11-06");
        assertThat(at(booking, "CURRENT", "externalRef")).containsEntry("written", "250905");
        assertThat(at(booking, "CURRENT", "propertyName")).containsEntry("written", "Haus Seeufer - Nordhorn");
        assertThat(at(booking, "CURRENT", "expectedRent")).containsEntry("source_value", "248.58");
        assertThat(at(booking, "CURRENT", "guestName")).containsEntry("written", "Erika Musterfrau");
        assertThat(at(booking, "CURRENT", "guestCity")).containsEntry("written", "Musterstadt");
    }

    @Test
    void laterMailsCompleteOnlyWhatTheyAdd() throws Exception {
        var booking = imported();
        var first = sourceId(sources(booking).getFirst());

        // Not confirmed yet: a second confirmation with another phone number completes the booking.
        deliver("fewo-confirmation-adults.eml", "second-250905", "0151 0000001", "0151 0000009");
        assertThat(inboxItem("Neue Buchung", "ATTACHED").get("resultDetail")).isEqualTo("Angaben ergänzt");
        var second = sourceId(sources(booking).get(1));
        assertThat(sourceOf(booking, "CURRENT", "guestPhone")).isEqualTo(second);
        assertThat(at(booking, "CURRENT", "guestPhone")).containsEntry("written", "0151 0000009");
        assertThat(sourceOf(booking, "CURRENT", "arrival")).isEqualTo(first);

        // A mail with the same values adds a source and keeps every location.
        deliver("fewo-confirmation-adults.eml", "third-250905", "0151 0000001", "0151 0000009");
        assertThat(sources(booking)).hasSize(3);
        assertThat(sourceOf(booking, "CURRENT", "arrival")).isEqualTo(first);
        assertThat(sourceOf(booking, "CURRENT", "guestPhone")).isEqualTo(second);
    }

    @Test
    void appliedChangeTakesItsLocations() throws Exception {
        var booking = imported();
        var first = sourceId(sources(booking).getFirst());
        postJson("/api/vacationrental/bookings/" + booking + "/confirm", Map.of(), anna.session())
                .andExpect(status().isOk());

        deliver("fewo-confirmation-adults.eml", "change-250905", "08.11.2026", "09.11.2026");
        assertThat(inboxItem("Neue Buchung", "CHANGE_PROPOSED")).isNotNull();
        var change = sourceId(sources(booking).get(1));
        assertThat(at(booking, "PROPOSAL", "departure")).containsEntry("written", "09.11.2026")
                .containsEntry("source_value", "2026-11-09");
        assertThat(sourceOf(booking, "PROPOSAL", "departure")).isEqualTo(change);
        assertThat(sourceOf(booking, "CURRENT", "departure")).isEqualTo(first);

        postJson("/api/vacationrental/bookings/" + booking + "/proposal/apply", Map.of(), anna.session())
                .andExpect(status().isOk());
        assertThat(sourceOf(booking, "CURRENT", "departure")).isEqualTo(change);
        assertThat(sourceOf(booking, "CURRENT", "arrival")).isEqualTo(first);
        assertThat(at(booking, "PROPOSAL", "departure")).isNull();
    }

    @Test
    void discardedChangeKeepsTheLocations() throws Exception {
        var booking = imported();
        var first = sourceId(sources(booking).getFirst());
        postJson("/api/vacationrental/bookings/" + booking + "/confirm", Map.of(), anna.session())
                .andExpect(status().isOk());
        deliver("fewo-confirmation-adults.eml", "change-250905", "08.11.2026", "09.11.2026");

        postJson("/api/vacationrental/bookings/" + booking + "/proposal/discard", Map.of(), anna.session())
                .andExpect(status().isOk());
        assertThat(sourceOf(booking, "CURRENT", "departure")).isEqualTo(first);
        assertThat(at(booking, "PROPOSAL", "departure")).isNull();

        // A cancellation replaces nothing but proposes without locations.
        deliver("fewo-cancellation-250905.eml", null);
        assertThat(sources(booking)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.booking_field_sources "
                + "WHERE booking_id = ? AND scope = 'PROPOSAL'", Long.class, booking)).isZero();
    }

    @Test
    void deletingGuestDataDropsOnlyTheGuestLocations() throws Exception {
        var booking = imported();
        mvc.perform(delete("/api/vacationrental/bookings/" + booking + "/guest").with(csrf()).session(anna.session()))
                .andExpect(status().isOk());
        assertThat(at(booking, "CURRENT", "guestName")).isNull();
        assertThat(at(booking, "CURRENT", "guestPhone")).isNull();
        assertThat(at(booking, "CURRENT", "arrival")).isNotNull();
        assertThat(sources(booking)).hasSize(1);
    }

    @Test
    void deletingAMailDocumentRemovesItsSource() throws Exception {
        var booking = imported();
        var first = sourceId(sources(booking).getFirst());
        deliver("fewo-confirmation-adults.eml", "second-250905", "0151 0000001", "0151 0000009");
        var secondDocument = jdbc.queryForObject("SELECT document_id FROM vacationrental.booking_sources "
                + "WHERE booking_id = ? AND id <> ?", Long.class, booking, first);

        mvc.perform(delete("/api/documents/" + secondDocument).with(csrf()).session(anna.session()))
                .andExpect(status().is2xxSuccessful());
        assertThat(sources(booking)).extracting(s -> s.get("id")).containsExactly(first);
        assertThat(at(booking, "CURRENT", "guestPhone")).isNull();
        assertThat(sourceOf(booking, "CURRENT", "arrival")).isEqualTo(first);
    }

    @Test
    void blockNoticeAddsASourceWithoutLocations() throws Exception {
        deliver("ds-vvv-confirmation.eml", null);
        var id = ((Number) inboxItem("Buchungsmitteilung", null).get("id")).longValue();
        postJson("/api/vacationrental/mail/" + id + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        var booking = ((Number) inboxItem("Buchungsmitteilung", "BOOKING_CREATED").get("linkId")).longValue();
        deliver("fewo-block-notice.eml", null);
        var block = ((Number) inboxItem("Neue Blockung", null).get("id")).longValue();
        postJson("/api/vacationrental/mail/" + block + "/assign", Map.of("unitId", unit), anna.session())
                .andExpect(status().isNoContent());
        assertThat(inboxItem("Neue Blockung", "ATTACHED")).isNotNull();

        var sources = sources(booking);
        assertThat(sources).hasSize(2);
        assertThat(sources.get(1).get("label")).isEqualTo("Fewo & Meer Blockung");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.booking_field_sources "
                + "WHERE source_id = ?", Long.class, sources.get(1).get("id"))).isZero();
    }

    @Test
    void bookingViewCarriesSourcesAndLocations() throws Exception {
        var booking = imported();
        var source = sourceId(sources(booking).getFirst());
        var response = getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].label").value("Fewo & Meer Buchungsbestätigung"))
                .andExpect(jsonPath("$.locations.arrival.sourceId").value(source))
                .andExpect(jsonPath("$.locations.arrival.part").value("TEXT"))
                .andExpect(jsonPath("$.locations.arrival.differs").value(false))
                .andExpect(jsonPath("$.locations.propertyName.part").value("SUBJECT"))
                .andReturn().getResponse().getContentAsString();
        int start = JsonPath.read(response, "$.locations.guestName.start");
        int end = JsonPath.read(response, "$.locations.guestName.end");
        var text = getWith("/api/vacationrental/bookings/" + booking + "/sources/" + source, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Neue Buchung für Haus Seeufer - Nordhorn"))
                .andExpect(jsonPath("$.sender").value("buchung@fewoundmeer.de"))
                .andReturn().getResponse().getContentAsString();
        String body = JsonPath.read(text, "$.text");
        assertThat(body.substring(start, end)).isEqualTo("Erika Musterfrau");
        // The list carries no sources.
        getWith("/api/vacationrental/bookings?unitId=" + unit, anna.session())
                .andExpect(jsonPath("$.items[0].sources").doesNotExist())
                .andExpect(jsonPath("$.items[0].locations").doesNotExist());
    }

    @Test
    void editedValueDiffersFromItsSourceUntilEditedBack() throws Exception {
        var booking = imported();
        var change = stay(unit, "2026-11-06", "2026-11-08");
        change.put("externalRef", "250905");
        change.put("adults", 4);
        change.put("expectedRent", "248.58");
        change.put("guest", Map.of("name", "Erika Muster-Frau"));
        change.put("version", 0);
        putJson("/api/vacationrental/bookings/" + booking, change, anna.session()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locations.guestName.differs").value(true))
                .andExpect(jsonPath("$.locations.guestName.sourceValue").value("Erika Musterfrau"))
                .andExpect(jsonPath("$.locations.arrival.differs").value(false));
        change.put("guest", Map.of("name", "Erika Musterfrau"));
        change.put("version", 1);
        putJson("/api/vacationrental/bookings/" + booking, change, anna.session()).andExpect(status().isOk())
                .andExpect(jsonPath("$.locations.guestName.differs").value(false));
    }

    @Test
    void viewersSeeNoSources() throws Exception {
        var booking = imported();
        var source = sourceId(sources(booking).getFirst());
        var dieter = actor();
        share(anna, "property", property, dieter, "VIEWER");
        getWith("/api/vacationrental/bookings/" + booking, dieter.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(0)))
                .andExpect(jsonPath("$.locations").isEmpty());
        getWith("/api/vacationrental/bookings/" + booking + "/sources/" + source, dieter.session())
                .andExpect(status().isNotFound());
        // A source of another booking is not found through this one.
        var other = booking(anna, unit, "2027-03-01", "2027-03-03");
        getWith("/api/vacationrental/bookings/" + other + "/sources/" + source, anna.session())
                .andExpect(status().isNotFound());
    }
}
