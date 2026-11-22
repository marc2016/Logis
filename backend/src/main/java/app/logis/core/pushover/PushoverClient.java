package app.logis.core.pushover;

/**
 * The calls Logis makes to the Pushover API (design D7 of notifications-pushover). Only what is passed here leaves
 * the system.
 */
interface PushoverClient {

    /** Checks an application token on its own; throws {@link PushoverException} if it is not accepted. */
    void checkToken(String token);

    /** Checks a user key with the application token: valid and with at least one active device. */
    void checkUser(String token, String userKey);

    /** Sends a message; title and text are shortened to what Pushover accepts. {@code link} may be null. */
    void send(String token, String userKey, String title, String text, String link);

    class PushoverException extends RuntimeException {

        enum Kind {
            /** Pushover does not know the application token. */
            INVALID_TOKEN,
            /** Pushover does not know the user key, or the account has no active device. */
            INVALID_USER,
            /** The monthly message limit of the Pushover account is reached. */
            LIMIT_REACHED,
            /** Any other request Pushover rejects; repeating it would not help. */
            REJECTED,
            /** Pushover could not be reached or has temporary problems. */
            UNAVAILABLE
        }

        private final Kind kind;

        PushoverException(Kind kind, Throwable cause) {
            super("Pushover: " + kind, cause);
            this.kind = kind;
        }

        Kind kind() {
            return kind;
        }
    }
}
