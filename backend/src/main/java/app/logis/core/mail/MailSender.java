package app.logis.core.mail;

/**
 * Sends plain-text system mails through the outgoing mail server the admin configured (spec outgoing-mail). Part
 * of the public API of the core.
 */
public interface MailSender {

    /** Whether an outgoing mail server is configured. */
    boolean configured();

    /** Sends a plain-text mail; throws {@link MailSendException} if sending is not set up or failed. */
    void send(String to, String subject, String text);

    class MailSendException extends RuntimeException {

        private final boolean notConfigured;

        public MailSendException(String message, boolean notConfigured, Throwable cause) {
            super(message, cause);
            this.notConfigured = notConfigured;
        }

        /** Sending is not set up: no server was asked. */
        public boolean notConfigured() {
            return notConfigured;
        }
    }
}
