package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vacationrental")
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class MailImportController {

    private final MailboxService mailboxes;
    private final MailImportService imports;
    private final MailRecognizer recognizer;
    private final TaskExecutor executor;

    MailImportController(MailboxService mailboxes, MailImportService imports, MailRecognizer recognizer,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.mailboxes = mailboxes;
        this.imports = imports;
        this.recognizer = recognizer;
        this.executor = executor;
    }

    /** Never contains the password, only whether one is stored. */
    record MailboxView(boolean configured, String host, Integer port, String username, boolean hasPassword,
            String folder, Boolean useTls, LocalDate cutoffDate, Boolean enabled, Instant lastPollAt,
            String lastError, boolean aiEnabled) {
    }

    record FetchOlderRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
    }

    record AssignRequest(Long unitId) {
    }

    record InboxItem(Long id, Instant receivedAt, String sender, String subject, String agency,
            ParsedMail.MailType mailType, String propertyName, String bookingRef, MailRecognizer.Source parsedBy,
            ImportedMail.Result result, String resultDetail, Long bookingId) {

        static InboxItem of(ImportedMail m) {
            var p = m.getParsed();
            return new InboxItem(m.getId(), m.getReceivedAt(), m.getSender(), m.getSubject(), m.getAgency(),
                    m.getMailType(), p == null ? null : p.propertyName(), p == null ? null : p.bookingRef(),
                    m.getParsedBy(), m.getResult(), m.getResultDetail(), m.getBookingId());
        }
    }

    record MailText(Long id, String subject, String text) {
    }

    @GetMapping("/mailbox")
    MailboxView mailbox(@AuthenticationPrincipal LogisPrincipal me) {
        return mailboxes.get(me.userId()).map(this::view)
                .orElse(new MailboxView(false, null, null, null, false, null, null, null, null, null, null,
                        recognizer.aiEnabled()));
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
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    void pollNow(@AuthenticationPrincipal LogisPrincipal me) {
        if (mailboxes.get(me.userId()).isEmpty()) {
            throw ApiException.notFound("Kein Postfach eingerichtet");
        }
        executor.execute(() -> imports.poll(me.userId()));
    }

    @PostMapping("/mailbox/fetch-older")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    void fetchOlder(@RequestBody FetchOlderRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        if (body.from() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        if (mailboxes.get(me.userId()).isEmpty()) {
            throw ApiException.notFound("Kein Postfach eingerichtet");
        }
        executor.execute(() -> imports.fetchOlder(me.userId(), body.from()));
    }

    @GetMapping("/inbox")
    List<InboxItem> inbox(@AuthenticationPrincipal LogisPrincipal me) {
        return imports.inbox(me.userId()).stream().map(InboxItem::of).toList();
    }

    @GetMapping("/inbox/{id}/text")
    MailText text(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        var mail = imports.get(me.userId(), id);
        return new MailText(mail.getId(), mail.getSubject(), mail.getTextPreview());
    }

    @PostMapping("/inbox/{id}/assign")
    InboxItem assign(@PathVariable Long id, @RequestBody AssignRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return InboxItem.of(imports.assignUnit(me.userId(), id, body.unitId()));
    }

    @PostMapping("/inbox/{id}/retry")
    InboxItem retry(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return InboxItem.of(imports.retry(me.userId(), id));
    }

    @PostMapping("/inbox/{id}/ignore")
    InboxItem ignore(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return InboxItem.of(imports.ignore(me.userId(), id));
    }

    @DeleteMapping("/inbox/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        imports.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private MailboxView view(Mailbox m) {
        return new MailboxView(true, m.getHost(), m.getPort(), m.getUsername(), true, m.getFolder(), m.isUseTls(),
                m.getCutoffDate(), m.isEnabled(), m.getLastPollAt(), m.getLastError(), recognizer.aiEnabled());
    }
}
