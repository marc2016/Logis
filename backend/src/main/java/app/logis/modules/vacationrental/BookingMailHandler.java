package app.logis.modules.vacationrental;

import app.logis.core.mail.Handled;
import app.logis.core.mail.MailContent;
import app.logis.core.mail.MailHandler;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Booking mails of rental agencies: recognise them (rules, then optional AI) and apply the effect on bookings. */
@Component
@Order(10)
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingMailHandler implements MailHandler {

    static final String KEY = "vacationrental.booking";

    private final MailRecognizer recognizer;
    private final ImportEngine engine;

    BookingMailHandler(MailRecognizer recognizer, ImportEngine engine) {
        this.recognizer = recognizer;
        this.engine = engine;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public boolean usesAi() {
        return recognizer.aiEnabled();
    }

    @Override
    public Optional<Handled> handle(Long ownerUserId, MailContent mail, byte[] raw) {
        var recognition = recognizer.recognize(mail);
        var parsed = recognition.parsed();
        if (parsed == null) {
            return Optional.empty();
        }
        var outcome = engine.apply(ownerUserId, parsed, raw);
        return Optional.of(new Handled(outcome.result().name(), outcome.detail(), parsed.type().name(),
                parsed.agency(), parsed.bookingRef(), parsed.propertyName(),
                outcome.bookingId() == null ? null : Booking.DOCUMENT_TYPE, outcome.bookingId(), parsed,
                recognition.source().name()));
    }
}
