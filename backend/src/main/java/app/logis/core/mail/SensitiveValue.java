package app.logis.core.mail;

/**
 * A value in a mail that the admin export must pseudonymize (design.md of admin-mail-export, D3). The kind decides
 * the shape of the replacement.
 */
public record SensitiveValue(Kind kind, String value) {

    public enum Kind {
        NAME, STREET, POSTAL_CODE, CITY, PHONE, EMAIL, REFERENCE
    }

    /** Null or blank values are dropped. */
    public static java.util.Optional<SensitiveValue> of(Kind kind, String value) {
        return value == null || value.isBlank() ? java.util.Optional.empty()
                : java.util.Optional.of(new SensitiveValue(kind, value.strip()));
    }
}
