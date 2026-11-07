package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import app.logis.shared.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Fetches mails of a mailbox and processes each one on its own: store it, recognise it, apply the effect, record
 * the result. A failing mail never stops the others.
 */
@Service
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class MailImportService {

    private static final Logger log = LoggerFactory.getLogger(MailImportService.class);
    static final String NOT_FOUND = "Mail nicht gefunden";

    private final MailboxRepository mailboxes;
    private final ImportedMailRepository mails;
    private final UnitMappingRepository mappings;
    private final ImapClient imap;
    private final MailRecognizer recognizer;
    private final ImportEngine engine;
    private final RentalAccess access;
    private final SecretCipher cipher;
    private final Clock clock;

    MailImportService(MailboxRepository mailboxes, ImportedMailRepository mails, UnitMappingRepository mappings,
            ImapClient imap, MailRecognizer recognizer, ImportEngine engine, RentalAccess access, SecretCipher cipher,
            Clock clock) {
        this.mailboxes = mailboxes;
        this.mails = mails;
        this.mappings = mappings;
        this.imap = imap;
        this.recognizer = recognizer;
        this.engine = engine;
        this.access = access;
        this.cipher = cipher;
        this.clock = clock;
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
        var mail = mails.save(new ImportedMail(ownerId, fetched.messageId(), fetched.receivedAt(), fetched.raw(),
                clock.instant()));
        handle(mail);
    }

    /** Recognises the stored mail again and applies its effect. */
    private void handle(ImportedMail mail) {
        try {
            var content = MailContent.parse(mail.getRaw());
            var recognition = recognizer.recognize(content);
            mail.read(content, recognition);
            if (recognition.parsed() == null) {
                mail.result(ImportedMail.Result.NOT_RECOGNISED, null, recognizer.aiEnabled() ? null
                        : "Kein bekanntes Format, KI ist ausgeschaltet");
            } else {
                var outcome = engine.apply(mail.getOwnerUserId(), recognition.parsed(), mail.getRaw());
                mail.result(outcome.result(), outcome.bookingId(), outcome.detail());
            }
        } catch (RuntimeException e) {
            log.warn("Processing mail {} failed", mail.getId(), e);
            mail.result(ImportedMail.Result.FAILED, null, "Verarbeitung fehlgeschlagen");
        }
        mails.save(mail);
    }

    // --- Inbox ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<ImportedMail> inbox(Long userId) {
        return mails.findForOwner(userId);
    }

    @Transactional(readOnly = true)
    ImportedMail get(Long userId, Long mailId) {
        return mails.findByIdAndOwnerUserId(mailId, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    /** Remembers the unit for the mail's property name and processes all mails waiting for it. */
    ImportedMail assignUnit(Long userId, Long mailId, Long unitId) {
        var mail = get(userId, mailId);
        var parsed = mail.getParsed();
        if (parsed == null || parsed.propertyName() == null) {
            throw ApiException.badRequest("Die Mail nennt kein Objekt.");
        }
        access.require(unitId, userId, ShareRole.EDITOR, BookingService.UNIT_NOT_FOUND);
        var mapping = mappings.find(userId, parsed.agency(), parsed.propertyName())
                .orElseGet(() -> new UnitMapping(userId, parsed.agency(), parsed.propertyName(), unitId));
        mapping.setUnitId(unitId);
        mappings.save(mapping);
        for (var waiting : mails.findWaiting(userId, ImportedMail.Result.UNASSIGNED_UNIT, parsed.agency())) {
            if (waiting.getParsed() != null
                    && parsed.propertyName().equalsIgnoreCase(waiting.getParsed().propertyName())) {
                handle(waiting);
            }
        }
        return get(userId, mailId);
    }

    ImportedMail retry(Long userId, Long mailId) {
        var mail = get(userId, mailId);
        if (!mail.getResult().retryable()) {
            throw ApiException.conflict("Diese Mail wurde bereits verarbeitet.");
        }
        handle(mail);
        return get(userId, mailId);
    }

    ImportedMail ignore(Long userId, Long mailId) {
        var mail = get(userId, mailId);
        mail.result(ImportedMail.Result.IGNORED, mail.getBookingId(), null);
        return mails.save(mail);
    }

    /**
     * Removes the stored mail content (and the guest data in it); the booking keeps its own copy. The Message-ID
     * stays known, so the mail is not fetched again.
     */
    void delete(Long userId, Long mailId) {
        var mail = get(userId, mailId);
        mail.forget();
        mails.save(mail);
    }

    @EventListener
    @Transactional
    void onDiscarded(ImportedBookingDiscarded event) {
        mails.markDiscarded(event.bookingId());
    }

    ImapClient.Config config(Mailbox mailbox) {
        return new ImapClient.Config(mailbox.getHost(), mailbox.getPort(), mailbox.getUsername(),
                cipher.decrypt(mailbox.getPasswordEnc()), mailbox.getFolder(), mailbox.isUseTls());
    }
}
