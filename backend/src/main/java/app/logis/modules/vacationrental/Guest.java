package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Guest data of a booking; everything but the name is contact data, visible only from EDITOR on. */
@Embeddable
record Guest(
        @Column(name = "guest_name", length = 128) String name,
        @Column(name = "guest_street", length = 128) String street,
        @Column(name = "guest_postal_code", length = 16) String postalCode,
        @Column(name = "guest_city", length = 128) String city,
        @Column(name = "guest_country", length = 64) String country,
        @Column(name = "guest_phone", length = 64) String phone,
        @Column(name = "guest_email", length = 254) String email) {

    static final Guest EMPTY = new Guest(null, null, null, null, null, null, null);

    /** Only the name, for users below EDITOR. */
    Guest nameOnly() {
        return new Guest(name, null, null, null, null, null, null);
    }

    boolean isEmpty() {
        return equals(EMPTY);
    }
}
