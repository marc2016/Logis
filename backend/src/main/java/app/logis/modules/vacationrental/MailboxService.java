package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import app.logis.shared.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** The user's own mailbox settings; the connection is tested before saving. */
@Service
@Transactional
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class MailboxService {

    static final String UNREACHABLE = "Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen";

    private final MailboxRepository mailboxes;
    private final ImapClient imap;
    private final SecretCipher cipher;

    MailboxService(MailboxRepository mailboxes, ImapClient imap, SecretCipher cipher) {
        this.mailboxes = mailboxes;
        this.imap = imap;
        this.cipher = cipher;
    }

    /** {@code password} may be empty to keep the stored one, as long as host and username stay the same. */
    record MailboxInput(String host, Integer port, String username, String password, String folder, Boolean useTls,
            LocalDate cutoffDate, Boolean enabled) {
    }

    @Transactional(readOnly = true)
    Optional<Mailbox> get(Long userId) {
        return mailboxes.findById(userId);
    }

    Mailbox save(Long userId, MailboxInput input) {
        var host = required(input.host(), "Bitte den IMAP-Server angeben.");
        var username = required(input.username(), "Bitte den Benutzernamen angeben.");
        var folder = input.folder() == null || input.folder().isBlank() ? "INBOX" : input.folder().strip();
        var useTls = input.useTls() == null || input.useTls();
        var port = input.port() != null ? input.port() : useTls ? 993 : 143;
        if (port < 1 || port > 65535) {
            throw ApiException.badRequest("Ungültiger Port.");
        }
        if (input.cutoffDate() == null) {
            throw ApiException.badRequest("Bitte einen Stichtag angeben.");
        }
        var existing = mailboxes.findById(userId);
        String password;
        if (input.password() != null && !input.password().isEmpty()) {
            password = input.password();
        } else if (existing.isPresent() && existing.get().getHost().equalsIgnoreCase(host)
                && existing.get().getUsername().equals(username)) {
            password = cipher.decrypt(existing.get().getPasswordEnc());
        } else {
            throw ApiException.badRequest("Bitte das Passwort angeben.");
        }
        try {
            imap.test(new ImapClient.Config(host, port, username, password, folder, useTls));
        } catch (ImapClient.ImapException e) {
            throw ApiException.badRequest(UNREACHABLE);
        }
        var mailbox = existing.orElseGet(() -> new Mailbox(userId));
        mailbox.apply(host, port, username, cipher.encrypt(password), folder, useTls, input.cutoffDate(),
                input.enabled() == null || input.enabled());
        return mailboxes.save(mailbox);
    }

    void delete(Long userId) {
        mailboxes.findById(userId).ifPresent(mailboxes::delete);
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
