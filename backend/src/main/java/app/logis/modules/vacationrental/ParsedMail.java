package app.logis.modules.vacationrental;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The fields read from a booking mail; {@code null} where the mail does not carry a field.
 *
 * @param agency       the rental agency, e.g. "Fewo & Meer" or "Holidu"
 * @param propertyName the property as the agency calls it, mapped to a unit per agency
 * @param intermediary who a block notice was made for, e.g. "VVV Nordhorn"; {@code null} for other mails
 */
record ParsedMail(String agency, MailType type, String bookingRef, String propertyName, LocalDate arrival,
        LocalDate departure, Integer adults, Integer children, Integer pets, BigDecimal expectedRent, Guest guest,
        String intermediary) {

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
