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

    @PostMapping("/mailbox/poll")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void pollNow(@AuthenticationPrincipal LogisPrincipal me) {
        if (mailboxes.get(me.userId()).isEmpty()) {
            throw ApiException.notFound("Kein Postfach eingerichtet");
        }
        executor.execute(() -> mails.poll(me.userId()));
    }

    @PostMapping("/mailbox/fetch-older")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void fetchOlder(@RequestBody FetchOlderRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        if (body.from() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        if (mailboxes.get(me.userId()).isEmpty()) {
            throw ApiException.notFound("Kein Postfach eingerichtet");
        }
        executor.execute(() -> mails.fetchOlder(me.userId(), body.from()));
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
