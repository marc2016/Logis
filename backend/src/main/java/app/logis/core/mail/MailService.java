package app.logis.core.mail;

import app.logis.core.web.ApiException;
import app.logis.shared.SecretCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Fetches mails of a mailbox and processes each one on its own: store it, offer it to the handlers, record the
 * result. Not transactional as a whole: a failing mail never stops the others.
 */
@Service
class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    static final String NOT_FOUND = "Mail nicht gefunden";

    private final MailboxRepository mailboxes;
    private final StoredMailRepository mails;
    private final ImapClient imap;
    private final List<MailHandler> handlers;
    private final SecretCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;

    MailService(MailboxRepository mailboxes, StoredMailRepository mails, ImapClient imap, List<MailHandler> handlers,
            SecretCipher cipher, ObjectMapper json, Clock clock) {
        this.mailboxes = mailboxes;
        this.mails = mails;
        this.imap = imap;
        this.handlers = handlers;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    boolean aiEnabled() {
        return handlers.stream().anyMatch(MailHandler::usesAi);
    }

    /** Regular poll: from the cut-off date, or one day before the last successful poll if that is later. */
    void poll(Long ownerId) {
        var mailbox = mailboxes.findById(ownerId).orElse(null);
        if (mailbox == null || !mailbox.isEnabled()) {
            return;
        }
        var since = mailbox.getCutoffDate();
        if (mailbox.getLastPollAt() != null) {
            var last = LocalDate.ofInstant(mailbox.getLastPollAt(), clock.getZone()).minusDays(1);
            if (last.isAfter(since)) {
                since = last;
            }
        }
        fetch(mailbox, since);
    }

    /** One-off fetch of older mails from a chosen date. */
    void fetchOlder(Long ownerId, LocalDate from) {
        var mailbox = mailboxes.findById(ownerId).orElseThrow(() -> ApiException.notFound("Kein Postfach eingerichtet"));
        fetch(mailbox, from);
    }

    private void fetch(Mailbox mailbox, LocalDate since) {
        var owner = mailbox.getOwnerUserId();
        String error = null;
        try {
            var fetched = imap.fetchSince(config(mailbox), since,
                    id -> mails.existsByOwnerUserIdAndMessageId(owner, id));
            for (var mail : fetched) {
                process(owner, mail);
            }
        } catch (ImapClient.ImapException e) {
            error = "Abruf fehlgeschlagen: " + e.getMessage();
            log.info("Mail poll for user {} failed: {}", owner, e.getMessage());
        }
        var fresh = mailboxes.findById(owner).orElse(null);
        if (fresh != null) {
            fresh.polled(clock.instant(), error);
            mailboxes.save(fresh);
        }
    }

    void process(Long ownerId, ImapClient.FetchedMail fetched) {
        if (mails.existsByOwnerUserIdAndMessageId(ownerId, fetched.messageId())) {
            return;
        }
        handle(mails.save(new StoredMail(ownerId, fetched.messageId(), fetched.receivedAt(), fetched.raw(),
                clock.instant())));
    }

    /** Offers the stored mail to the handlers in order; the first result wins. */
    void handle(StoredMail mail) {
        try {
            var content = MailContent.parse(mail.getRaw());
            mail.read(content);
            mail.unhandled(StoredMail.NOT_RECOGNISED, handlers.isEmpty() ? null
                    : aiEnabled() ? null : "Kein bekanntes Format");
            for (var handler : handlers) {
                var handled = handler.handle(mail.getOwnerUserId(), content, mail.getRaw());
                if (handled.isPresent()) {
                    mail.handled(handler.key(), handled.get(), toJson(handled.get().data()));
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Processing mail {} failed", mail.getId(), e);
            mail.unhandled(StoredMail.FAILED, "Verarbeitung fehlgeschlagen");
        }
        mails.save(mail);
    }

    private String toJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // --- Inbox ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<StoredMail> inbox(Long userId) {
        return mails.findForOwner(userId);
    }

    @Transactional(readOnly = true)
    StoredMail get(Long userId, Long mailId) {
        return mails.findOwn(mailId, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    StoredMail retry(Long userId, Long mailId) {
        var mail = get(userId, mailId);
        if (!mail.retryable()) {
            throw ApiException.conflict("Diese Mail wurde bereits verarbeitet.");
        }
        handle(mail);
        return get(userId, mailId);
    }

    StoredMail ignore(Long userId, Long mailId) {
        var mail = get(userId, mailId);
        mail.ignore();
        return mails.save(mail);
    }

    /**
     * Removes the stored mail content (and the guest data in it); copies attached elsewhere stay. The Message-ID
     * stays known, so the mail is not fetched again.
     */
    void delete(Long userId, Long mailId) {
        var mail = get(userId, mailId);
        mail.forget();
        mails.save(mail);
    }

    ImapClient.Config config(Mailbox mailbox) {
        return new ImapClient.Config(mailbox.getHost(), mailbox.getPort(), mailbox.getUsername(),
                cipher.decrypt(mailbox.getPasswordEnc()), mailbox.getFolder(), mailbox.isUseTls());
    }
}
