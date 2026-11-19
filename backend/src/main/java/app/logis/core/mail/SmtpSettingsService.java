package app.logis.core.mail;

import app.logis.core.web.ApiException;
import app.logis.shared.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/** The admin's outgoing mail server; the connection is tested before saving (design D3 of outgoing-mail). */
@Service
@Transactional
class SmtpSettingsService {

    static final String UNREACHABLE = "Der Mailserver ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen";
    static final String TEST_FAILED = "Die Testmail konnte nicht gesendet werden";
    static final String TEST_SUBJECT = "Testmail von Logis";

    private final SmtpSettingsRepository settings;
    private final SmtpClient smtp;
    private final SecretCipher cipher;
    private final MailSender sender;
    private final Clock clock;

    SmtpSettingsService(SmtpSettingsRepository settings, SmtpClient smtp, SecretCipher cipher, MailSender sender,
            Clock clock) {
        this.settings = settings;
        this.smtp = smtp;
        this.cipher = cipher;
        this.sender = sender;
        this.clock = clock;
    }

    /** {@code password} may be empty to keep the stored one, as long as host and username stay the same. */
    record Input(String host, Integer port, SmtpSettings.Security security, String username, String password,
            String fromAddress, String fromName) {
    }

    @Transactional(readOnly = true)
    Optional<SmtpSettings> get() {
        return settings.current();
    }

    SmtpSettings save(Input input) {
        var host = required(input.host(), "Bitte den Mailserver angeben.");
        var security = input.security() == null ? SmtpSettings.Security.STARTTLS : input.security();
        var port = input.port() != null ? input.port() : switch (security) {
            case NONE -> 25;
            case STARTTLS -> 587;
            case SSL_TLS -> 465;
        };
        if (port < 1 || port > 65535) {
            throw ApiException.badRequest("Ungültiger Port.");
        }
        var username = input.username() == null || input.username().isBlank() ? null : input.username().strip();
        var from = EmailAddress.require(input.fromAddress(), "Bitte eine gültige Absenderadresse angeben.");
        var fromName = input.fromName() == null || input.fromName().isBlank() ? null : input.fromName().strip();
        if (fromName != null && fromName.length() > 128) {
            throw ApiException.badRequest("Der Absendername ist zu lang.");
        }
        var existing = settings.current();
        String password = null;
        if (input.password() != null && !input.password().isEmpty()) {
            password = input.password();
        } else if (username != null && existing.isPresent() && existing.get().getPasswordEnc() != null
                && existing.get().getHost().equalsIgnoreCase(host)
                && username.equals(existing.get().getUsername())) {
            password = cipher.decrypt(existing.get().getPasswordEnc());
        } else if (username != null) {
            throw ApiException.badRequest("Bitte das Passwort angeben.");
        }
        try {
            smtp.test(new SmtpClient.Config(host, port, security, username, password));
        } catch (SmtpClient.SmtpException ex) {
            throw ApiException.badRequest(UNREACHABLE);
        }
        var entity = existing.orElseGet(SmtpSettings::create);
        entity.apply(host, port, security, username, password == null ? null : cipher.encrypt(password), from,
                fromName, clock.instant());
        return settings.save(entity);
    }

    void delete() {
        settings.current().ifPresent(settings::delete);
    }

    void sendTest(String to) {
        var address = EmailAddress.require(to, "Bitte eine gültige E-Mail-Adresse angeben.");
        try {
            sender.send(address, TEST_SUBJECT, """
                    Diese Testmail bestätigt, dass Logis über den eingerichteten Mailserver Mails versenden kann.

                    Du musst nichts weiter tun.""");
        } catch (MailSender.MailSendException ex) {
            throw ApiException.badRequest(TEST_FAILED);
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        if (value.strip().length() > 255) {
            throw ApiException.badRequest("Die Eingabe ist zu lang.");
        }
        return value.strip();
    }
}
