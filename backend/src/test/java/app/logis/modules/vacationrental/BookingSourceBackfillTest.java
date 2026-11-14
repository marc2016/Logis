package app.logis.modules.vacationrental;

import app.logis.core.documents.Documents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bookings imported before sources existed get them from their attached mails (booking-sources spec). */
class BookingSourceBackfillTest extends VacationRentalTest {

    @Autowired
    Documents documents;

    @Autowired
    BookingSourceBackfill backfill;

    private byte[] mail(String name, String... replacements) throws Exception {
        try (var in = getClass().getResourceAsStream("/mails/" + name)) {
            var text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (int i = 0; i < replacements.length; i += 2) {
                text = text.replace(replacements[i], replacements[i + 1]);
            }
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    private void attach(Long bookingId, byte[] raw) {
        documents.store(Booking.DOCUMENT_TYPE, bookingId, "mail.eml", "message/rfc822", raw);
    }

    private Map<String, Object> bookingRow(Long id) {
        return jdbc.queryForMap("SELECT * FROM vacationrental.bookings WHERE id = ?", id);
    }

    @Test
    void existingBookingGetsItsSourcesOnce() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var body = stay(unit, "2026-11-06", "2026-11-08");
        body.put("externalRef", "250905");
        body.put("adults", 4);
        body.put("guest", Map.of("name", "Erika Musterfrau"));
        var booking = ((Number) com.jayway.jsonpath.JsonPath.read(book(anna, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        attach(booking, mail("fewo-confirmation-adults.eml"));
        // A change that was never applied, and a mail no rule knows.
        attach(booking, mail("fewo-confirmation-adults.eml", "08.11.2026", "09.11.2026"));
        attach(booking, mail("unknown-change.eml"));
        var before = bookingRow(booking);

        backfill.run(null);

        assertThat(bookingRow(booking)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.booking_sources WHERE booking_id = ?",
                Long.class, booking)).isEqualTo(2);
        getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(jsonPath("$.sources[0].label").value("Fewo & Meer Buchungsbestätigung"))
                .andExpect(jsonPath("$.locations.departure.sourceValue").value("2026-11-09"))
                .andExpect(jsonPath("$.locations.departure.differs").value(true))
                .andExpect(jsonPath("$.locations.arrival.differs").value(false))
                .andExpect(jsonPath("$.locations.guestName.differs").value(false));

        backfill.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.booking_sources WHERE booking_id = ?",
                Long.class, booking)).isEqualTo(2);
    }

    @Test
    void blockNoticesAndCancellationsAddNoLocations() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var body = stay(unit, "2027-05-15", "2027-05-18");
        body.put("externalRef", "3e98f765kl");
        var booking = ((Number) com.jayway.jsonpath.JsonPath.read(book(anna, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        attach(booking, mail("ds-vvv-confirmation.eml"));
        // The block notice names Fewo & Meer's own number 250904 for the same stay.
        attach(booking, mail("fewo-block-notice.eml"));
        attach(booking, mail("fewo-cancellation-250905.eml"));

        backfill.run(null);

        getWith("/api/vacationrental/bookings/" + booking, anna.session())
                .andExpect(jsonPath("$.sources.length()").value(3))
                .andExpect(jsonPath("$.locations.externalRef.sourceValue").value("3e98f765kl"))
                .andExpect(jsonPath("$.locations.externalRef.differs").value(false))
                .andExpect(jsonPath("$.locations.arrival.sourceValue").value("2027-05-15"));
    }
}
