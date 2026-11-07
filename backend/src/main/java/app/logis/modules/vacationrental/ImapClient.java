package app.logis.modules.vacationrental;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/** Read-only access to an IMAP folder. */
interface ImapClient {

    record Config(String host, int port, String username, String password, String folder, boolean useTls) {
    }

    record FetchedMail(String messageId, Instant receivedAt, byte[] raw) {
    }

    /** Connects, logs in and opens the folder; throws {@link ImapException} otherwise. */
    void test(Config config);

    /**
     * Mails received on or after {@code since} whose Message-ID {@code known} does not accept yet. Never changes
     * flags or content of the folder.
     */
    List<FetchedMail> fetchSince(Config config, LocalDate since, Predicate<String> known);

    class ImapException extends RuntimeException {
        ImapException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
