package app.logis.modules.vacationrental;

import java.util.Arrays;
import java.util.Optional;

/** The fields of a booking that can be read from a source; {@link #key()} is the name in the API and the UI. */
enum BookingField {
    EXTERNAL_REF("externalRef"),
    PROPERTY_NAME("propertyName"),
    ARRIVAL("arrival"),
    DEPARTURE("departure"),
    ADULTS("adults"),
    CHILDREN("children"),
    PETS("pets"),
    EXPECTED_RENT("expectedRent"),
    GUEST_NAME("guestName"),
    GUEST_STREET("guestStreet"),
    GUEST_POSTAL_CODE("guestPostalCode"),
    GUEST_CITY("guestCity"),
    GUEST_COUNTRY("guestCountry"),
    GUEST_PHONE("guestPhone"),
    GUEST_EMAIL("guestEmail");

    private final String key;

    BookingField(String key) {
        this.key = key;
    }

    String key() {
        return key;
    }

    boolean isGuest() {
        return key.startsWith("guest");
    }

    static Optional<BookingField> ofKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }
}
