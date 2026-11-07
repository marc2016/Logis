package app.logis.core.mail;

import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API for handlers: look at an own mail, process waiting mails again, mark mails of a record. */
@Service
public class Mails {

    /** What a handler may know about a stored mail. */
    public record MailSummary(Long id, String handler, String result, String source, String subjectName) {
    }

    private final StoredMailRepository mails;
    private final MailService service;

    Mails(StoredMailRepository mails, MailService service) {
        this.mails = mails;
        this.service = service;
    }

    /** Fetches new mails of the user's mailbox now, synchronously. */
    public void pollNow(Long ownerUserId) {
        service.poll(ownerUserId);
    }

    /** Fetches the user's mails from a date once, synchronously. */
    public void fetchOlder(Long ownerUserId, java.time.LocalDate from) {
        service.fetchOlder(ownerUserId, from);
    }

    /** A mail of the user; 404 for anyone else's or a deleted one. */
    @Transactional(readOnly = true)
    public MailSummary get(Long ownerUserId, Long mailId) {
        var m = mails.findOwn(mailId, ownerUserId).orElseThrow(() -> ApiException.notFound(MailService.NOT_FOUND));
        return new MailSummary(m.getId(), m.getHandler(), m.getResult(), m.getSource(), m.getSubjectName());
    }

    /** Processes the owner's mails of this handler, result, source and subject again, e.g. after a mapping. */
    public void reprocessWaiting(Long ownerUserId, String handler, String result, String source, String subjectName) {
        for (var mail : mails.findWaiting(ownerUserId, handler, result, source, subjectName)) {
            service.handle(mail);
        }
    }

    /** Sets the result of all mails linked to a record, e.g. when a created booking is discarded. */
    @Transactional
    public void markByLink(String linkType, Long linkId, String result) {
        mails.markByLink(linkType, linkId, result);
    }
}
