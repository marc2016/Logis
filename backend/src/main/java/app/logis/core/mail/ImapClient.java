package app.logis.core.mail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Predicate;

/** Read-only access to an IMAP folder. */
interface ImapClient {

    record Config(String host, int port, String username, String password, String folder, boolean useTls) {
    }

    record FetchedMail(String messageId, Instant receivedAt, byte[] raw) {
    }

    /** Receives the mails of a fetch one by one, while the folder is still open. */
    interface Listener {

        /** Number of mails the search found; called once before any mail. */
        void onTotal(int total);

        /** A mail that is not known yet, downloaded completely. */
        void onMail(FetchedMail mail);

        /** A found mail that is known already or older than the requested date. */
        void onSkipped();
    }

    /** Connects, logs in and opens the folder; throws {@link ImapException} otherwise. */
    void test(Config config);

    /**
     * Hands mails received on or after {@code since} to the listener, downloading only those whose Message-ID
     * {@code known} does not accept yet. Never changes flags or content of the folder.
     */
    void fetchSince(Config config, LocalDate since, Predicate<String> known, Listener listener);

    class ImapException extends RuntimeException {
        ImapException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
