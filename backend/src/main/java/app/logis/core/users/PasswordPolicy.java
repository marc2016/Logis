package app.logis.core.users;

import app.logis.core.web.ApiException;

/**
 * Central password rules.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
    }

    public static void requireValid(String password) {
        if (!isValid(password)) {
            throw ApiException.badRequest("Das Passwort muss mindestens " + MIN_LENGTH + " Zeichen lang sein.");
        }
    }
}
