package app.logis.core.mail;

import app.logis.core.web.ApiException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/** Validation of plain e-mail addresses entered by users (design D5 of outgoing-mail). */
public final class EmailAddress {

    public static final String INVALID = "Bitte eine gültige E-Mail-Adresse angeben.";

    private EmailAddress() {
    }

    /**
     * The trimmed address if it is a single plain address (no display name, one @, at most 254 characters);
     * otherwise a 400 with {@code message}.
     */
    public static String require(String value, String message) {
        if (value == null || value.isBlank() || value.strip().length() > 254) {
            throw ApiException.badRequest(message);
        }
        var trimmed = value.strip();
        try {
            var parsed = new InternetAddress(trimmed, true);
            parsed.validate();
            if (parsed.getPersonal() != null || !trimmed.equals(parsed.getAddress())
                    || trimmed.indexOf('@') <= 0 || trimmed.indexOf('@') != trimmed.lastIndexOf('@')
                    || !trimmed.substring(trimmed.indexOf('@') + 1).contains(".")) {
                throw ApiException.badRequest(message);
            }
            return trimmed;
        } catch (AddressException ex) {
            throw ApiException.badRequest(message);
        }
    }
}
