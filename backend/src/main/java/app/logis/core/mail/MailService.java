package app.logis.core.mail;

import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.time.Duration;
import org.springframework.context.ApplicationEventPublisher;
import app.logis.core.notifications.Notifications;
import app.logis.core.notifications.NotificationType;
import app.logis.core.notifications.Notification;
import app.logis.core.web.ApiException;
import app.logis.shared.SecretCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches mails of a mailbox and processes each one on its own: store it, offer it to the handlers, record the
 * result. Not transactional as a whole: a failing mail never stops the others.
 */
@Service
class MailService {

    /**
     * Days such as the cut-off date are the user's days: the browser sends them in local time, while the server clock
     * runs in UTC. Around midnight the two differ by a day, so day boundaries are taken in this zone.
     */
    static final java.time.ZoneId ZONE = java.time.ZoneId.of("Europe/Berlin");

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    static final String NOT_FOUND = "Mail nicht gefunden";
    static final String ALREADY_RUNNING = "Es läuft bereits ein Abruf.";

    private final MailboxRepository mailboxes;
    private final StoredMailRepository mails;
    private final ImapClient imap;
    private final List<MailHandler> handlers;
    private final SecretCipher cipher;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<Long, FetchRun> runs = new ConcurrentHashMap<>();
    private final Notifications notifications;
    private final ApplicationEventPublisher events;
    private final Set<String> attentionResults;

    /** Only mails received this recently count as new: a first poll from an old cut-off date stays quiet. */
    static final Duration FRESH = Duration.ofDays(2);
    static final NotificationType ATTENTION = NotificationType.of("core.mailAttention",
            "E-Mails brauchen Aufmerksamkeit", "Posteingang");
    static final NotificationType FETCH_FAILED = NotificationType.of("core.mailFetchFailed",
            "Postfach-Abruf fehlgeschlagen", "Posteingang");
    static final String INBOX_PATH = "/posteingang";

    MailService(MailboxRepository mailboxes, StoredMailRepository mails, ImapClient imap, List<MailHandler> handlers,
            SecretCipher cipher, ObjectMapper json, Clock clock, Notifications notifications,
            ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.events = events;
        var attention = new HashSet<>(Set.of(StoredMail.NOT_RECOGNISED, StoredMail.FAILED));
        handlers.forEach(handler -> attention.addAll(handler.attentionResults()));
        this.attentionResults = Set.copyOf(attention);
        this.mailboxes = mailboxes;
        this.mails = mails;
        this.imap = imap;
        this.handlers = handlers;
        this.cipher = cipher;
        this.json = json;
        this.clock = clock;
    }

    /** Whether the mail leaves something for the user to do: what the quick filter „Offen“ shows. */
    boolean needsAttention(StoredMail mail) {
        return attentionResults.contains(mail.getResult());
    }

    boolean aiEnabled() {
        return handlers.stream().anyMatch(MailHandler::usesAi);
    }

    /** Regular poll, synchronously; does nothing for a missing or disabled mailbox. */
    void poll(Long ownerId) {
        var run = beginPoll(ownerId, true);
        if (run != null) {
            run(run);
        }
    }

    /** Scheduled poll: skips the mailbox while another fetch of it runs. */
    void pollIfIdle(Long ownerId) {
        var run = beginPoll(ownerId, false);
        if (run != null) {
            run(run.automatic());
        }
    }

    /** One-off fetch of older mails from a chosen date, synchronously. */
    void fetchOlder(Long ownerId, LocalDate from) {
        run(beginOlder(ownerId, from));
    }

    /**
     * Reserves the mailbox for a regular poll: from the cut-off date, or one day before the last successful poll if
     * that is later. Returns null for a missing or disabled mailbox, and if a fetch runs and {@code failIfRunning}
     * is false; throws 409 if it is true.
     */
    FetchRun beginPoll(Long ownerId, boolean failIfRunning) {
        var mailbox = mailboxes.findById(ownerId).orElse(null);
        if (mailbox == null || !mailbox.isEnabled()) {
            return null;
        }
        var since = mailbox.getCutoffDate();
        if (mailbox.getLastPollAt() != null) {
            var last = LocalDate.ofInstant(mailbox.getLastPollAt(), ZONE).minusDays(1);
            if (last.isAfter(since)) {
                since = last;
            }
        }
        var run = reserve(ownerId, FetchRun.Kind.POLL, since);
        if (run == null && failIfRunning) {
            throw ApiException.conflict(ALREADY_RUNNING);
        }
        return run;
    }

    /** Reserves the mailbox for fetching older mails from a date; 409 if a fetch runs. */
    FetchRun beginOlder(Long ownerId, LocalDate from) {
        if (from == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        if (from.isAfter(LocalDate.ofInstant(clock.instant(), ZONE))) {
            throw ApiException.badRequest("Das Datum darf nicht in der Zukunft liegen.");
        }
        if (mailboxes.findById(ownerId).isEmpty()) {
            throw ApiException.notFound("Kein Postfach eingerichtet");
        }
        var run = reserve(ownerId, FetchRun.Kind.OLDER, from);
        if (run == null) {
            throw ApiException.conflict(ALREADY_RUNNING);
        }
        return run;
    }

    /** The current or last fetch of the user's mailbox, if any since the start of the application. */
    Optional<FetchRun> fetchRun(Long ownerId) {
        return Optional.ofNullable(runs.get(ownerId));
    }

    /** A new running fetch for the owner, or null if one runs already. */
    private FetchRun reserve(Long ownerId, FetchRun.Kind kind, LocalDate from) {
        var fresh = new FetchRun(ownerId, kind, from, clock.instant());
        var current = runs.compute(ownerId, (id, run) -> run != null && run.running() ? run : fresh);
        return current == fresh ? fresh : null;
    }

    /** Fetches and processes the mails of a reserved run one by one; always ends the run. */
    void run(FetchRun run) {
        var owner = run.ownerUserId();
        String error = null;
        var failedBefore = false;
        try {
            var mailbox = mailboxes.findById(owner)
                    .orElseThrow(() -> new IllegalStateException("Mailbox of user " + owner + " is gone"));
            failedBefore = mailbox.getLastError() != null;
            imap.fetchSince(config(mailbox), run.from(), id -> mails.existsByOwnerUserIdAndMessageId(owner, id),
                    new ImapClient.Listener() {
                        public void onTotal(int total) {
                            run.total(total);
                        }

                        public void onMail(ImapClient.FetchedMail mail) {
                            run.checked(process(run, mail));
                        }

                        public void onSkipped() {
                            run.checked(false);
                        }
                    });
        } catch (ImapClient.ImapException e) {
            error = "Abruf fehlgeschlagen: " + e.getMessage();
            log.info("Mail fetch for user {} failed: {}", owner, e.getMessage());
        } catch (RuntimeException e) {
            error = "Abruf fehlgeschlagen";
            log.warn("Mail fetch for user {} failed", owner, e);
        } finally {
            run.finish(clock.instant(), error);
        }
        var fresh = mailboxes.findById(owner).orElse(null);
        if (fresh != null) {
            fresh.polled(clock.instant(), error);
            mailboxes.save(fresh);
        }
        notifyAfter(run, error != null && !failedBefore);
    }

    /** One notification per poll for the mails that need attention, and one when the regular poll starts failing. */
    private void notifyAfter(FetchRun run, boolean startedFailing) {
        if (run.kind() != FetchRun.Kind.POLL) {
            return;
        }
        var owner = List.of(run.ownerUserId());
        var open = run.attention();
        if (!open.isEmpty()) {
            var one = open.size() == 1;
            var title = one ? "1 E-Mail braucht Aufmerksamkeit" : open.size() + " E-Mails brauchen Aufmerksamkeit";
            var shortText = one ? "Im Posteingang wartet eine E-Mail, die nicht automatisch verarbeitet wurde."
                    : "Im Posteingang warten " + open.size() + " E-Mails, die nicht automatisch verarbeitet wurden.";
            var longText = shortText + "\n\n" + open.stream().map(line -> "– " + line)
                    .collect(Collectors.joining("\n")) + "\n";
            notifications.send(ATTENTION.key(), owner,
                    new Notification(title, shortText, longText, INBOX_PATH + "?ergebnis=offen"));
        }
        // Only the scheduler's poll: a fetch the user starts shows its error on the page.
        if (startedFailing && run.isAutomatic()) {
            var text = "Logis konnte dein Postfach nicht abrufen. Bis der Abruf wieder klappt, werden keine neuen "
                    + "E-Mails verarbeitet. Bitte prüfe die Einstellungen des Postfachs im Posteingang.";
            notifications.send(FETCH_FAILED.key(), owner,
                    new Notification("Postfach-Abruf fehlgeschlagen", text, text + "\n", INBOX_PATH));
        }
    }

    /** Stores and handles a fetched mail outside a fetch; false if its Message-ID is known already. */
    boolean process(Long ownerId, ImapClient.FetchedMail fetched) {
        return store(ownerId, fetched) != null;
    }

    /** As {@link #process(Long, ImapClient.FetchedMail)}, and tells the rest of Logis about new mails of a poll. */
    private boolean process(FetchRun run, ImapClient.FetchedMail fetched) {
        var stored = store(run.ownerUserId(), fetched);
        if (stored == null) {
            return false;
        }
        var fresh = stored.getReceivedAt() != null && !stored.getReceivedAt().isBefore(clock.instant().minus(FRESH));
        if (run.kind() == FetchRun.Kind.POLL && fresh) {
            if (needsAttention(stored)) {
                run.needsAttention((stored.getSender() == null ? "Unbekannter Absender" : stored.getSender()) + ": "
                        + (stored.getSubject() == null || stored.getSubject().isBlank() ? "(ohne Betreff)"
                        : stored.getSubject()));
            }
            try {
                events.publishEvent(new MailImported(stored.getOwnerUserId(), stored.getId(), stored.getHandler(),
                        stored.getResult(), stored.getLinkType(), stored.getLinkId()));
            } catch (RuntimeException e) {
                // A listener must never stop the fetch.
                log.warn("Listener for imported mail {} failed", stored.getId(), e);
            }
        }
        return true;
    }

    /** The stored and handled mail, or null if its Message-ID is known already. */
    private StoredMail store(Long ownerId, ImapClient.FetchedMail fetched) {
        if (mails.existsByOwnerUserIdAndMessageId(ownerId, fetched.messageId())) {
            return null;
        }
        StoredMail stored;
        try {
            stored = mails.save(new StoredMail(ownerId, fetched.messageId(), fetched.receivedAt(), fetched.raw(),
                    clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Stored meanwhile by someone else: the unique Message-ID keeps it a single mail.
            return null;
        }
        handle(stored);
        return stored;
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
