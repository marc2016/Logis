package app.logis.core.mail;

/** Access to an outgoing mail server (design D2 of outgoing-mail). */
interface SmtpClient {

    record Config(String host, int port, SmtpSettings.Security security, String username, String password) {
    }

    record Mail(String fromAddress, String fromName, String to, String subject, String text) {
    }

    /** Connects and authenticates, then closes; throws {@link SmtpException} otherwise. */
    void test(Config config);

    /** Throws {@link SmtpException} if the mail cannot be sent. */
    void send(Config config, Mail mail);

    class SmtpException extends RuntimeException {

        SmtpException(Throwable cause) {
            super(cause);
        }
    }
}
