package app.logis.core.mail;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.web.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/mail")
class MailController {

    private final MailboxService mailboxes;
    private final MailService mails;
    private final TaskExecutor executor;

    MailController(MailboxService mailboxes, MailService mails,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.mailboxes = mailboxes;
        this.mails = mails;
        this.executor = executor;
    }

    /** Never contains the password, only whether one is stored. */
    record MailboxView(boolean configured, String host, Integer port, String username, boolean hasPassword,
            String folder, Boolean useTls, LocalDate cutoffDate, Boolean enabled, Instant lastPollAt,
            String lastError, boolean aiEnabled) {
    }

    record FetchOlderRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
    }

    record InboxItem(Long id, Instant receivedAt, String sender, String subject, String handler, String kind,
            String source, String reference, String subjectName, String parsedBy, String result, String resultDetail,
            String linkType, Long linkId, boolean retryable) {

        static InboxItem of(StoredMail m) {
            return new InboxItem(m.getId(), m.getReceivedAt(), m.getSender(), m.getSubject(), m.getHandler(),
                    m.getKind(), m.getSource(), m.getReference(), m.getSubjectName(), m.getParsedBy(), m.getResult(),
                    m.getResultDetail(), m.getLinkType(), m.getLinkId(), m.retryable());
        }
    }

    record MailText(Long id, String subject, String text) {
    }

    @GetMapping("/mailbox")
    MailboxView mailbox(@AuthenticationPrincipal LogisPrincipal me) {
        return mailboxes.get(me.userId()).map(this::view)
                .orElse(new MailboxView(false, null, null, null, false, null, null, null, null, null, null,
                        mails.aiEnabled()));
    }

    @PutMapping("/mailbox")
    MailboxView saveMailbox(@RequestBody MailboxService.MailboxInput body, @AuthenticationPrincipal LogisPrincipal me) {
        return view(mailboxes.save(me.userId(), body));
    }

    @DeleteMapping("/mailbox")
    ResponseEntity<Void> deleteMailbox(@AuthenticationPrincipal LogisPrincipal me) {
        mailboxes.delete(me.userId());
        return ResponseEntity.noContent().build();
    }

    /** Progress of the current or last fetch; {@code total} is null until the folder has been searched. */
    record FetchStatus(FetchRun.Kind kind, LocalDate from, Integer total, int checked, int created,
            FetchRun.State state, String error, Instant startedAt, Instant finishedAt) {

        static FetchStatus of(FetchRun r) {
            return new FetchStatus(r.kind(), r.from(), r.total(), r.checked(), r.created(), r.state(), r.error(),
                    r.startedAt(), r.finishedAt());
        }
    }

    /** Empty body if the mailbox is switched off: then nothing is fetched, as before. */
    @PostMapping("/mailbox/poll")
    ResponseEntity<FetchStatus> pollNow(@AuthenticationPrincipal LogisPrincipal me) {
        if (mailboxes.get(me.userId()).isEmpty()) {
            throw ApiException.notFound("Kein Postfach eingerichtet");
        }
        var run = mails.beginPoll(me.userId(), true);
        if (run == null) {
            return ResponseEntity.accepted().build();
        }
        executor.execute(() -> mails.run(run));
        return ResponseEntity.accepted().body(FetchStatus.of(run));
    }

    @PostMapping("/mailbox/fetch-older")
    @ResponseStatus(HttpStatus.ACCEPTED)
    FetchStatus fetchOlder(@RequestBody FetchOlderRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        var run = mails.beginOlder(me.userId(), body.from());
        executor.execute(() -> mails.run(run));
        return FetchStatus.of(run);
    }

    /** The user's own fetch only; empty body when there was none since the start of the application. */
    @GetMapping("/mailbox/fetch-status")
    ResponseEntity<FetchStatus> fetchStatus(@AuthenticationPrincipal LogisPrincipal me) {
        return mails.fetchRun(me.userId()).map(FetchStatus::of).map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/inbox")
    List<InboxItem> inbox(@AuthenticationPrincipal LogisPrincipal me) {
        return mails.inbox(me.userId()).stream().map(InboxItem::of).toList();
    }

    @GetMapping("/inbox/{id}/text")
    MailText text(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        var mail = mails.get(me.userId(), id);
        return new MailText(mail.getId(), mail.getSubject(), mail.getTextPreview());
    }

    @PostMapping("/inbox/{id}/retry")
    InboxItem retry(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return InboxItem.of(mails.retry(me.userId(), id));
    }

    @PostMapping("/inbox/{id}/ignore")
    InboxItem ignore(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return InboxItem.of(mails.ignore(me.userId(), id));
    }

    @DeleteMapping("/inbox/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        mails.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private MailboxView view(Mailbox m) {
        return new MailboxView(true, m.getHost(), m.getPort(), m.getUsername(), true, m.getFolder(), m.isUseTls(),
                m.getCutoffDate(), m.isEnabled(), m.getLastPollAt(), m.getLastError(), mails.aiEnabled());
    }
}
