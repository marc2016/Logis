package app.logis.modules.vacationrental;

import app.logis.core.documents.Documents;
import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Derives sources and locations once for bookings imported before sources existed, from their attached mail
 * documents (design.md D8). Rules only, oldest mail first, one transaction per booking; never changes booking data.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingSourceBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookingSourceBackfill.class);
    static final String MAIL_TYPE = "message/rfc822";

    private final Documents documents;
    private final BookingSourceStore store;
    private final BookingSources sources;
    private final BookingRepository bookings;
    private final MailRecognizer recognizer;
    private final TransactionTemplate tx;

    BookingSourceBackfill(Documents documents, BookingSourceStore store, BookingSources sources,
            BookingRepository bookings, MailRecognizer recognizer, TransactionTemplate tx) {
        this.documents = documents;
        this.store = store;
        this.sources = sources;
        this.bookings = bookings;
        this.recognizer = recognizer;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        var derived = 0;
        for (var bookingId : documents.ownerIds(Booking.DOCUMENT_TYPE, MAIL_TYPE)) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> derive(bookingId)))) {
                    derived++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not derive the sources of booking {}: {}", bookingId, e.getMessage());
            }
        }
        if (derived > 0) {
            log.info("Derived sources for {} existing bookings", derived);
        }
    }

    private static boolean carriesValues(ParsedMail.MailType type) {
        return type == ParsedMail.MailType.CONFIRMATION || type == ParsedMail.MailType.SHORT_NOTICE
                || type == ParsedMail.MailType.CHANGE;
    }

    /** Sources for one booking without any; {@code true} if at least one mail was recognised. */
    boolean derive(Long bookingId) {
        if (store.hasSources(bookingId) || !bookings.existsById(bookingId)) {
            return false;
        }
        var any = false;
        for (var document : documents.contents(Booking.DOCUMENT_TYPE, bookingId, MAIL_TYPE)) {
            MailContent mail;
            try {
                mail = MailContent.parse(document.content());
            } catch (IllegalArgumentException e) {
                continue;
            }
            var parsed = recognizer.recognizeByRules(mail);
            if (parsed.isEmpty()) {
                continue;
            }
            var p = parsed.get();
            var source = sources.addMail(bookingId, document.id(), p.agency() + " " + ImportEngine.label(p.type()),
                    mail);
            // As on import, only mails that carry the booking's values locate them: a block notice names the
            // agency's own number, a cancellation proposes no values. A later mail's locations replace earlier ones.
            if (carriesValues(p.type())) {
                sources.setCurrent(bookingId, source, p, BookingSources.fields(p));
            }
            any = true;
        }
        return any;
    }
}
