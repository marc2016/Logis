package app.logis.modules.vacationrental;

import app.logis.core.mail.MailImported;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.notifications.Notification;
import app.logis.core.notifications.NotificationType;
import app.logis.core.notifications.Notifications;
import app.logis.core.realestate.UnitSummary;
import app.logis.core.sharing.ShareRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Notifications about bookings from mails that wait for review (spec bookings, „Notify about bookings awaiting
 * review“; design D6 of notifications-pushover). The short text names property, period and agency only; guest,
 * amounts and booking numbers appear in the long text alone.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingNotifications {

    static final String GROUP = "Ferienvermietung";
    static final NotificationType BOOKING_REVIEW = NotificationType.of("vacationrental.bookingReview",
            "Neue Buchung zu prüfen", GROUP);
    static final NotificationType CHANGE_REVIEW = NotificationType.of("vacationrental.changeReview",
            "Änderung oder Stornierung zu prüfen", GROUP);
    static final String BOOKINGS_PATH = "/ferienvermietung/buchungen";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BookingRepository bookings;
    private final AgencyRepository agencies;
    private final RentalAccess access;
    private final Notifications notifications;

    BookingNotifications(BookingRepository bookings, AgencyRepository agencies, RentalAccess access,
            Notifications notifications) {
        this.bookings = bookings;
        this.agencies = agencies;
        this.access = access;
        this.notifications = notifications;
    }

    @Bean
    static NotificationType bookingReviewNotificationType() {
        return BOOKING_REVIEW;
    }

    @Bean
    static NotificationType changeReviewNotificationType() {
        return CHANGE_REVIEW;
    }

    /** A new mail of a regular poll created a booking or proposed a change: tell those who can confirm it. */
    @EventListener
    @Transactional(readOnly = true)
    void onMailImported(MailImported event) {
        if (!BookingMailHandler.KEY.equals(event.handlerKey()) || event.linkId() == null
                || !Booking.DOCUMENT_TYPE.equals(event.linkType())) {
            return;
        }
        var created = BookingMailResult.BOOKING_CREATED.name().equals(event.result());
        var proposed = BookingMailResult.CHANGE_PROPOSED.name().equals(event.result());
        var booking = created || proposed ? bookings.findById(event.linkId()).orElse(null) : null;
        if (booking == null) {
            return;
        }
        var unit = access.summaries(List.of(booking.getUnitId())).get(booking.getUnitId());
        if (unit == null) {
            return;
        }
        // Only users who can confirm or discard are asked to.
        var recipients = access.usersOn(booking.getUnitId(), ShareRole.EDITOR);
        var agency = booking.getAgencyId() == null ? null
                : agencies.findById(booking.getAgencyId()).map(Agency::getName).orElse(null);
        if (created && booking.getReviewState() == ReviewState.NEW) {
            notifications.send(BOOKING_REVIEW.key(), recipients, newBooking(booking, unit, agency));
        } else if (proposed && booking.getProposal() != null) {
            notifications.send(CHANGE_REVIEW.key(), recipients, proposal(booking, unit, agency));
        }
    }

    static Notification newBooking(Booking booking, UnitSummary unit, String agency) {
        var lines = new ArrayList<String>();
        lines.add("Eine E-Mail hat eine neue Buchung angelegt. Sie wartet auf deine Prüfung.");
        lines.add("");
        lines.addAll(details(booking, unit, agency));
        return new Notification("Neue Buchung zu prüfen", summary(booking, unit, agency),
                String.join("\n", lines) + "\n", BOOKINGS_PATH + "/" + booking.getId());
    }

    static Notification proposal(Booking booking, UnitSummary unit, String agency) {
        var proposal = booking.getProposal();
        var cancellation = proposal.type() == BookingProposal.Type.CANCELLATION;
        var lines = new ArrayList<String>();
        lines.add(cancellation ? "Eine E-Mail schlägt vor, diese Buchung zu stornieren. Der Vorschlag wartet auf "
                + "deine Prüfung." : "Eine E-Mail schlägt eine Änderung dieser Buchung vor. Der Vorschlag wartet auf "
                + "deine Prüfung.");
        lines.add("");
        lines.addAll(details(booking, unit, agency));
        if (!cancellation) {
            var changes = new ArrayList<String>();
            if (proposal.arrival() != null || proposal.departure() != null) {
                changes.add("Neuer Zeitraum: " + period(proposal.arrival() != null ? proposal.arrival()
                        : booking.getArrival(), proposal.departure() != null ? proposal.departure()
                        : booking.getDeparture()));
            }
            if (proposal.adults() != null || proposal.children() != null) {
                changes.add("Neue Personenzahl: " + persons(proposal.adults() != null ? proposal.adults()
                        : booking.getAdults(), proposal.children() != null ? proposal.children()
                        : booking.getChildren()));
            }
            if (proposal.expectedRent() != null) {
                changes.add("Neue erwartete Miete: " + euro(proposal.expectedRent()));
            }
            if (proposal.guest() != null && proposal.guest().name() != null) {
                changes.add("Neuer Gast: " + proposal.guest().name());
            }
            if (!changes.isEmpty()) {
                lines.add("");
                lines.addAll(changes);
            }
        }
        return new Notification(cancellation ? "Stornierung zu prüfen" : "Änderung zu prüfen",
                summary(booking, unit, agency) + (cancellation ? ": Stornierung vorgeschlagen"
                        : ": Änderung vorgeschlagen"),
                String.join("\n", lines) + "\n", BOOKINGS_PATH + "/" + booking.getId());
    }

    /** Property, period and agency: what may leave the system. */
    private static String summary(Booking booking, UnitSummary unit, String agency) {
        return unitLabel(unit) + ", " + period(booking.getArrival(), booking.getDeparture())
                + (agency == null ? "" : ", über " + agency);
    }

    private static List<String> details(Booking booking, UnitSummary unit, String agency) {
        var lines = new ArrayList<String>();
        lines.add("Objekt: " + unitLabel(unit));
        lines.add("Zeitraum: " + period(booking.getArrival(), booking.getDeparture()));
        if (agency != null) {
            lines.add("Vermittler: " + agency);
        }
        if (booking.getGuest() != null && booking.getGuest().name() != null) {
            lines.add("Gast: " + booking.getGuest().name());
        }
        lines.add("Personen: " + persons(booking.getAdults(), booking.getChildren()));
        if (booking.getExpectedRent() != null) {
            lines.add("Erwartete Miete: " + euro(booking.getExpectedRent()));
        }
        if (booking.getExternalRef() != null) {
            lines.add("Buchungsnummer: " + booking.getExternalRef());
        }
        return lines;
    }

    /** The property, with the unit where the property shows its units. */
    static String unitLabel(UnitSummary unit) {
        return unit.propertyMultiUnit() ? unit.propertyName() + ", " + unit.name() : unit.propertyName();
    }

    static String period(LocalDate arrival, LocalDate departure) {
        var nights = java.time.temporal.ChronoUnit.DAYS.between(arrival, departure);
        return DAY.format(arrival) + " – " + DAY.format(departure) + " (" + nights
                + (nights == 1 ? " Nacht)" : " Nächte)");
    }

    static String persons(int adults, int children) {
        var text = adults + (adults == 1 ? " Erwachsener" : " Erwachsene");
        return children == 0 ? text : text + ", " + children + (children == 1 ? " Kind" : " Kinder");
    }

    static String euro(BigDecimal amount) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(amount) + " €";
    }
}
