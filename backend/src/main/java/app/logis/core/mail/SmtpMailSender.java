package app.logis.core.mail;

import app.logis.shared.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the settings per send, so a new configuration applies without restart (design D4 of outgoing-mail). */
@Service
class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final SmtpSettingsRepository settings;
    private final SmtpClient smtp;
    private final SecretCipher cipher;

    SmtpMailSender(SmtpSettingsRepository settings, SmtpClient smtp, SecretCipher cipher) {
        this.settings = settings;
        this.smtp = smtp;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean configured() {
        return settings.current().isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public void send(String to, String subject, String text) {
        var current = settings.current()
                .orElseThrow(() -> new MailSendException("Der E-Mail-Versand ist nicht eingerichtet.", true, null));
        try {
            smtp.send(config(current, cipher),
                    new SmtpClient.Mail(current.getFromAddress(), current.getFromName(), to, subject, text));
        } catch (SmtpClient.SmtpException ex) {
            // Never the text or the full address: only where it should have gone and why it failed.
            var cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("Mail to domain {} could not be sent: {}", domain(to), cause.getClass().getSimpleName());
            throw new MailSendException("Die Mail konnte nicht gesendet werden.", false, ex);
        }
    }

    static SmtpClient.Config config(SmtpSettings s, SecretCipher cipher) {
        return new SmtpClient.Config(s.getHost(), s.getPort(), s.getSecurity(), s.getUsername(),
                s.getPasswordEnc() == null ? null : cipher.decrypt(s.getPasswordEnc()));
    }

    private static String domain(String address) {
        var at = address == null ? -1 : address.lastIndexOf('@');
        return at < 0 ? "?" : address.substring(at + 1);
    }
}
