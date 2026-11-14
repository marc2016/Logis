package app.logis.modules.vacationrental;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * The fields read from a booking mail; {@code null} where the mail does not carry a field.
 *
 * @param agency       the rental agency, e.g. "Fewo & Meer" or "Holidu"
 * @param propertyName the property as the agency calls it, mapped to a unit per agency
 * @param intermediary who a block notice was made for, e.g. "VVV Nordhorn"; {@code null} for other mails
 * @param locations    where each read value was found in the mail; not part of what the inbox stores
 */
record ParsedMail(String agency, MailType type, String bookingRef, String propertyName, LocalDate arrival,
        LocalDate departure, Integer adults, Integer children, Integer pets, BigDecimal expectedRent, Guest guest,
        String intermediary, @JsonIgnore Map<BookingField, Location> locations) {

    ParsedMail {
        locations = locations == null ? Map.of() : Map.copyOf(locations);
    }

    ParsedMail(String agency, MailType type, String bookingRef, String propertyName, LocalDate arrival,
            LocalDate departure, Integer adults, Integer children, Integer pets, BigDecimal expectedRent,
            Guest guest, String intermediary) {
        this(agency, type, bookingRef, propertyName, arrival, departure, adults, children, pets, expectedRent, guest,
                intermediary, Map.of());
    }

    ParsedMail withLocations(Map<BookingField, Location> locations) {
        return new ParsedMail(agency, type, bookingRef, propertyName, arrival, departure, adults, children, pets,
                expectedRent, guest, intermediary, locations);
    }

    enum MailType {
        CONFIRMATION,
        SHORT_NOTICE,
        CHANGE,
        CANCELLATION,
        /** The agency blocked its calendar for a stay booked elsewhere; never creates a booking. */
        BLOCK_NOTICE
    }

    ParsedMail(String agency, MailType type, String bookingRef, String propertyName, LocalDate arrival,
            LocalDate departure, Integer adults, Integer children, Integer pets, BigDecimal expectedRent,
            Guest guest) {
        this(agency, type, bookingRef, propertyName, arrival, departure, adults, children, pets, expectedRent, guest,
                null);
    }

    /** Complete enough to act on: agency, type and booking number, and dates unless it is a cancellation. */
    boolean usable() {
        return agency != null && type != null && bookingRef != null && !bookingRef.isBlank()
                && (type == MailType.CANCELLATION || arrival != null && departure != null
                        && departure.isAfter(arrival));
    }
}
