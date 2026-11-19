package app.logis.modules.vacationrental;

import app.logis.core.mail.Handled;
import app.logis.core.mail.MailContent;
import app.logis.core.mail.MailHandler;
import app.logis.core.mail.SensitiveValue;
import app.logis.core.mail.SensitiveValue.Kind;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Booking mails of rental agencies: recognise them (rules, then optional AI) and apply the effect on bookings. */
@Component
@Order(10)
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingMailHandler implements MailHandler {

    static final String KEY = "vacationrental.booking";

    private final MailRecognizer recognizer;
    private final ImportEngine engine;
    private final RentalAccess access;
    private final BookingRepository bookings;

    BookingMailHandler(MailRecognizer recognizer, ImportEngine engine, RentalAccess access,
            BookingRepository bookings) {
        this.recognizer = recognizer;
        this.engine = engine;
        this.access = access;
        this.bookings = bookings;
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
        var outcome = engine.apply(ownerUserId, parsed, mail, raw);
        return Optional.of(new Handled(outcome.result().name(), outcome.detail(), parsed.type().name(),
                parsed.agency(), parsed.bookingRef(), parsed.propertyName(),
                outcome.bookingId() == null ? null : Booking.DOCUMENT_TYPE, outcome.bookingId(), parsed,
                recognition.source().name()));
    }

    /**
     * For the admin export: guest values and booking numbers of all bookings the owner can see, collected once, plus
     * what the rules read from each mail. Rules only - the AI is never asked (design D3 of admin-mail-export).
     */
    @Override
    @Transactional(readOnly = true)
    public SensitiveValues sensitiveValues(Long ownerUserId) {
        var known = new HashSet<SensitiveValue>();
        var units = access.visibleUnits(ownerUserId).keySet();
        if (!units.isEmpty()) {
            for (var booking : bookings.findForUnits(units, null, null)) {
                addGuest(known, booking.getGuest());
                SensitiveValue.of(Kind.REFERENCE, booking.getExternalRef()).ifPresent(known::add);
            }
        }
        var owned = Set.copyOf(known);
        return mail -> {
            var values = new HashSet<>(owned);
            recognizer.recognizeByRules(mail).ifPresent(parsed -> {
                addGuest(values, parsed.guest());
                SensitiveValue.of(Kind.REFERENCE, parsed.bookingRef()).ifPresent(values::add);
            });
            return values;
        };
    }

    private static void addGuest(Set<SensitiveValue> values, Guest guest) {
        if (guest == null) {
            return;
        }
        SensitiveValue.of(Kind.NAME, guest.name()).ifPresent(values::add);
        SensitiveValue.of(Kind.STREET, guest.street()).ifPresent(values::add);
        SensitiveValue.of(Kind.POSTAL_CODE, guest.postalCode()).ifPresent(values::add);
        SensitiveValue.of(Kind.CITY, guest.city()).ifPresent(values::add);
        SensitiveValue.of(Kind.PHONE, guest.phone()).ifPresent(values::add);
        SensitiveValue.of(Kind.EMAIL, guest.email()).ifPresent(values::add);
    }
}
