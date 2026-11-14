package app.logis.modules.vacationrental;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * What the AI returns for a mail. All fields are strings so the schema stays simple; an empty string means
 * "not in the mail". Converted and validated by {@link AiMailParser}.
 */
record AiExtraction(
        @JsonPropertyDescription("true if this is a booking mail of a rental agency, else false") boolean bookingMail,
        @JsonPropertyDescription("Name of the rental agency, e.g. \"Fewo & Meer\" or \"Holidu\"") String agency,
        @JsonPropertyDescription("CONFIRMATION, SHORT_NOTICE, CHANGE or CANCELLATION") String type,
        @JsonPropertyDescription("The agency's booking number") String bookingRef,
        @JsonPropertyDescription("Name of the property or holiday home as written in the mail") String propertyName,
        @JsonPropertyDescription("Arrival date as YYYY-MM-DD") String arrival,
        @JsonPropertyDescription("Departure date as YYYY-MM-DD") String departure,
        @JsonPropertyDescription("Number of adults as digits") String adults,
        @JsonPropertyDescription("Number of children as digits") String children,
        @JsonPropertyDescription("Number of pets as digits") String pets,
        @JsonPropertyDescription("Total price the booking earns, as a decimal number with a dot, e.g. 309.08")
        String expectedRent,
        String guestName,
        String guestStreet,
        String guestPostalCode,
        String guestCity,
        String guestCountry,
        String guestPhone,
        String guestEmail,
        @JsonPropertyDescription("For each value you filled in, the exact passage of the mail you read it from, copied "
                + "character by character (e.g. \"Anreise: 23.12.2026\"). field is the property name, e.g. arrival, "
                + "guestName or bookingRef.")
        List<Quote> quotes) {

    record Quote(@JsonPropertyDescription("Name of the property the passage belongs to") String field,
            @JsonPropertyDescription("The passage, copied exactly from the mail") String passage) {
    }

    AiExtraction(boolean bookingMail, String agency, String type, String bookingRef, String propertyName,
            String arrival, String departure, String adults, String children, String pets, String expectedRent,
            String guestName, String guestStreet, String guestPostalCode, String guestCity, String guestCountry,
            String guestPhone, String guestEmail) {
        this(bookingMail, agency, type, bookingRef, propertyName, arrival, departure, adults, children, pets,
                expectedRent, guestName, guestStreet, guestPostalCode, guestCity, guestCountry, guestPhone, guestEmail,
                List.of());
    }
}
