package app.logis.modules.vacationrental;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Field values as plain strings and how they are compared, shared by the mail import (what a mail changes) and the
 * booking view (whether a value differs from its source), so both agree (design.md D7).
 */
final class FieldValues {

    private FieldValues() {
    }

    /** The value a mail carries for the field, normalized: ISO dates, plain decimals, stripped text. */
    static String of(ParsedMail m, BookingField field) {
        var g = m.guest() == null ? Guest.EMPTY : m.guest();
        return text(switch (field) {
            case EXTERNAL_REF -> m.bookingRef();
            case PROPERTY_NAME -> m.propertyName();
            case ARRIVAL -> m.arrival();
            case DEPARTURE -> m.departure();
            case ADULTS -> m.adults();
            case CHILDREN -> m.children();
            case PETS -> m.pets();
            case EXPECTED_RENT -> m.expectedRent();
            default -> guest(g, field);
        });
    }

    /** The booking's current value for the field; the property name is not stored on a booking. */
    static String of(Booking b, BookingField field) {
        return text(switch (field) {
            case EXTERNAL_REF -> b.getExternalRef();
            case PROPERTY_NAME -> null;
            case ARRIVAL -> b.getArrival();
            case DEPARTURE -> b.getDeparture();
            case ADULTS -> b.getAdults();
            case CHILDREN -> b.getChildren();
            case PETS -> b.getPets();
            case EXPECTED_RENT -> b.getExpectedRent();
            default -> guest(b.getGuest() == null ? Guest.EMPTY : b.getGuest(), field);
        });
    }

    /** Whether the booking's value still matches the value read from the source. */
    static boolean same(BookingField field, String current, String source) {
        if (field == BookingField.PROPERTY_NAME) {
            return true;
        }
        if (current == null || source == null) {
            return current == null && source == null;
        }
        return switch (field) {
            case GUEST_NAME -> sameName(current, source);
            case GUEST_PHONE -> samePhone(current, source);
            case EXPECTED_RENT -> new BigDecimal(current).compareTo(new BigDecimal(source)) == 0;
            case ARRIVAL, DEPARTURE, ADULTS, CHILDREN, PETS -> current.equals(source);
            default -> current.strip().equalsIgnoreCase(source.strip());
        };
    }

    /** "+49 151 0000001" and "0151 0000001" are the same number. */
    static boolean samePhone(String a, String b) {
        return a != null && b != null && phoneDigits(a).equals(phoneDigits(b));
    }

    private static String phoneDigits(String s) {
        var digits = s.strip().replaceAll("[^0-9+]", "");
        if (digits.startsWith("+49")) {
            return "0" + digits.substring(3);
        }
        if (digits.startsWith("0049")) {
            return "0" + digits.substring(4);
        }
        return digits.replace("+", "");
    }

    /** Names match regardless of word order ("Erika Musterfrau" = "Musterfrau Erika") and case. */
    static boolean sameName(String a, String b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return words(a).equals(words(b));
    }

    private static String words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[\\s,]+")).filter(w -> !w.isBlank()).sorted()
                .collect(Collectors.joining(" "));
    }

    private static String guest(Guest g, BookingField field) {
        return switch (field) {
            case GUEST_NAME -> g.name();
            case GUEST_STREET -> g.street();
            case GUEST_POSTAL_CODE -> g.postalCode();
            case GUEST_CITY -> g.city();
            case GUEST_COUNTRY -> g.country();
            case GUEST_PHONE -> g.phone();
            case GUEST_EMAIL -> g.email();
            default -> throw new IllegalArgumentException(field.name());
        };
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d.toPlainString();
        }
        var s = value.toString().strip();
        return s.isEmpty() ? null : s;
    }
}
