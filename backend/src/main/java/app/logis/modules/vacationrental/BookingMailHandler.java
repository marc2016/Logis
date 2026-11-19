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
    /** The core's result for ignored mails, labelled „Ignoriert“ by the inbox. */
    static final String IGNORED = "IGNORED";
    static final String TICKET_RECEIPT = "TICKET_RECEIPT";

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
        // Before every other rule and the AI: receipts carry no booking data (spec booking-mails, „Ticket receipts“).
        var receipt = recognizer.ticketReceipt(mail);
        if (receipt.isPresent()) {
            return Optional.of(new Handled(IGNORED, null, TICKET_RECEIPT, FewoUndMeerParser.AGENCY, receipt.get(),
                    null, null, null, null, MailRecognizer.Source.RULE.name()));
        }
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

    /** Same labels as {@code mailResultLabel} in the frontend's {@code modules/vacationrental/api.ts}. */
    static final java.util.Map<String, String> RESULT_LABELS = java.util.Map.of(
            "BOOKING_CREATED", "Buchung angelegt",
            "OWNER_USE_CREATED", "Eigenbelegung angelegt",
            "CHANGE_PROPOSED", "Änderung vorgeschlagen",
            "ATTACHED", "Angehängt",
            "UNASSIGNED_UNIT", "Einheit zuordnen",
            "NOT_MATCHED", "Nicht zugeordnet",
            "PERIOD_TAKEN", "Zeitraum belegt",
            "DISCARDED", "Verworfen");

    /** Same labels as {@code mailKindLabel} in the frontend's {@code modules/vacationrental/api.ts}. */
    static final java.util.Map<String, String> KIND_LABELS = java.util.Map.of(
            "CONFIRMATION", "Bestätigung",
            "SHORT_NOTICE", "Last-Minute",
            "CHANGE", "Änderung",
            "CANCELLATION", "Stornierung",
            "BLOCK_NOTICE", "Blockung",
            "OWNER_BLOCK", "Eigentümerblockung",
            "TICKET_RECEIPT", "Eingangsbestätigung");

    @Override
    public java.util.Set<String> agencyDomains() {
        return recognizer.agencyDomains();
    }

    @Override
    public java.util.Map<String, String> resultLabels() {
        return RESULT_LABELS;
    }

    @Override
    public java.util.Map<String, String> kindLabels() {
        return KIND_LABELS;
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
