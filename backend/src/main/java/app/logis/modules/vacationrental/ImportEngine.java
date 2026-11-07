package app.logis.modules.vacationrental;

import app.logis.core.documents.Documents;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Decides what a recognised mail does to the bookings of the mailbox owner (design.md D3). Every booking call runs
 * with the owner's rights and the usual rules; a failing call only affects this mail.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class ImportEngine {

    record Outcome(BookingMailResult result, Long bookingId, String detail) {
    }

    private final BookingService bookings;
    private final UnitMappingRepository mappings;
    private final RentalAccess access;
    private final Documents documents;

    ImportEngine(BookingService bookings, UnitMappingRepository mappings, RentalAccess access, Documents documents) {
        this.bookings = bookings;
        this.mappings = mappings;
        this.access = access;
        this.documents = documents;
    }

    Outcome apply(Long ownerId, ParsedMail mail, byte[] raw) {
        var existing = bookings.findByRef(ownerId, mail.agency(), mail.bookingRef()).stream().findFirst();
        try {
            return switch (mail.type()) {
                case CONFIRMATION, SHORT_NOTICE -> existing.isPresent()
                        ? update(ownerId, existing.get(), mail, raw)
                        : create(ownerId, mail, raw);
                case CHANGE -> existing.isPresent()
                        ? update(ownerId, existing.get(), mail, raw)
                        : new Outcome(BookingMailResult.NOT_MATCHED, null, "Buchung " + mail.bookingRef()
                                + " ist nicht erfasst");
                case CANCELLATION -> existing.isPresent()
                        ? cancel(ownerId, existing.get(), mail, raw)
                        : new Outcome(BookingMailResult.NOT_MATCHED, null, "Buchung " + mail.bookingRef()
                                + " ist nicht erfasst");
            };
        } catch (ApiException e) {
            return new Outcome(BookingMailResult.FAILED, existing.map(Booking::getId).orElse(null), e.getMessage());
        }
    }

    private Outcome create(Long ownerId, ParsedMail mail, byte[] raw) {
        var mapping = mail.propertyName() == null ? null
                : mappings.find(ownerId, mail.agency(), mail.propertyName()).orElse(null);
        if (mapping == null) {
            return new Outcome(BookingMailResult.UNASSIGNED_UNIT, null,
                    "Einheit für „" + Objects.toString(mail.propertyName(), "?") + "“ zuordnen");
        }
        var role = access.role(mapping.getUnitId(), ownerId).orElse(null);
        if (!RentalAccess.canEdit(role)) {
            return new Outcome(BookingMailResult.UNASSIGNED_UNIT, null,
                    "Keine Bearbeitungsrechte für die zugeordnete Einheit");
        }
        try {
            var created = bookings.createImported(ownerId, input(mapping.getUnitId(), mail, null));
            attach(created.booking().getId(), mail, raw);
            return new Outcome(BookingMailResult.BOOKING_CREATED, created.booking().getId(), null);
        } catch (ApiException e) {
            if (BookingService.OVERLAP.equals(e.getMessage())) {
                return new Outcome(BookingMailResult.PERIOD_TAKEN, null, e.getMessage());
            }
            throw e;
        }
    }

    private Outcome update(Long ownerId, Booking booking, ParsedMail mail, byte[] raw) {
        var id = booking.getId();
        bookings.adoptAgency(ownerId, id, mail.agency());
        var proposal = differences(booking, mail);
        if (proposal == null) {
            attach(id, mail, raw);
            return new Outcome(BookingMailResult.ATTACHED, id, null);
        }
        if (booking.getReviewState() == ReviewState.NEW) {
            // Not confirmed yet: the mail simply completes the prepared booking.
            bookings.updateImported(ownerId, id, input(booking.getUnitId(), merged(booking, proposal), booking));
            attach(id, mail, raw);
            return new Outcome(BookingMailResult.ATTACHED, id, "Angaben ergänzt");
        }
        bookings.propose(ownerId, id, proposal);
        attach(id, mail, raw);
        return new Outcome(BookingMailResult.CHANGE_PROPOSED, id, null);
    }

    private Outcome cancel(Long ownerId, Booking booking, ParsedMail mail, byte[] raw) {
        var id = booking.getId();
        bookings.adoptAgency(ownerId, id, mail.agency());
        attach(id, mail, raw);
        if (booking.isCancelled()) {
            return new Outcome(BookingMailResult.ATTACHED, id, "Bereits storniert");
        }
        bookings.propose(ownerId, id, BookingProposal.cancellation(null));
        return new Outcome(BookingMailResult.CHANGE_PROPOSED, id, "Stornierung");
    }

    private void attach(Long bookingId, ParsedMail mail, byte[] raw) {
        var label = switch (mail.type()) {
            case CONFIRMATION -> "Buchungsbestätigung";
            case SHORT_NOTICE -> "Last-Minute";
            case CHANGE -> "Änderung";
            case CANCELLATION -> "Stornierung";
        };
        documents.store(Booking.DOCUMENT_TYPE, bookingId, mail.agency() + " " + label + " " + mail.bookingRef() + ".eml",
                "message/rfc822", raw);
    }

    /** Fields the mail carries that differ from the booking, or {@code null} if nothing differs. */
    static BookingProposal differences(Booking b, ParsedMail m) {
        var arrival = m.arrival() != null && !m.arrival().equals(b.getArrival()) ? m.arrival() : null;
        var departure = m.departure() != null && !m.departure().equals(b.getDeparture()) ? m.departure() : null;
        var adults = m.adults() != null && m.adults() != b.getAdults() ? m.adults() : null;
        var children = m.children() != null && m.children() != b.getChildren() ? m.children() : null;
        var pets = m.pets() != null && m.pets() != b.getPets() ? m.pets() : null;
        var rent = m.expectedRent() != null && (b.getExpectedRent() == null
                || m.expectedRent().compareTo(b.getExpectedRent()) != 0) ? m.expectedRent() : null;
        var guest = guestDifferences(b.getGuest(), m.guest());
        if (arrival == null && departure == null && adults == null && children == null && pets == null
                && rent == null && guest == null) {
            return null;
        }
        return new BookingProposal(BookingProposal.Type.CHANGE, arrival, departure, adults, children, pets, rent,
                guest, null);
    }

    private static Guest guestDifferences(Guest current, Guest mail) {
        if (mail == null || mail.isEmpty()) {
            return null;
        }
        var name = mail.name() != null && !sameName(current.name(), mail.name()) ? mail.name() : null;
        var g = new Guest(name, differs(current.street(), mail.street()), differs(current.postalCode(),
                mail.postalCode()), differs(current.city(), mail.city()), differs(current.country(), mail.country()),
                samePhone(current.phone(), mail.phone()) ? null : differs(current.phone(), mail.phone()), differs(current.email(), mail.email()));
        return g.isEmpty() ? null : g;
    }

    private static String differs(String current, String mail) {
        if (mail == null || mail.isBlank()) {
            return null;
        }
        return current != null && current.strip().equalsIgnoreCase(mail.strip()) ? null : mail.strip();
    }

    /** "+49 151 0000001" and "0151 0000001" are the same number. */
    static boolean samePhone(String a, String b) {
        return a != null && b != null && phoneDigits(a).equals(phoneDigits(b));
    }

    private static String phoneDigits(String s) {
        var digits = s.strip().replaceAll("[^0-9+]", "");
        if (digits.startsWith("+49")) {
            return "0" + digits.substring(3);
        }
        if (digits.startsWith("0049")) {
            return "0" + digits.substring(4);
        }
        return digits.replace("+", "");
    }

    /** Names match regardless of word order ("Erika Musterfrau" = "Musterfrau Erika") and case. */
    static boolean sameName(String a, String b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return words(a).equals(words(b));
    }

    private static String words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[\\s,]+")).filter(w -> !w.isBlank()).sorted()
                .collect(Collectors.joining(" "));
    }

    /** The mail values on top of the booking, for completing a booking that awaits review. */
    private static ParsedMail merged(Booking b, BookingProposal p) {
        return new ParsedMail(null, null, b.getExternalRef(), null,
                p.arrival() != null ? p.arrival() : b.getArrival(),
                p.departure() != null ? p.departure() : b.getDeparture(),
                p.adults() != null ? p.adults() : b.getAdults(),
                p.children() != null ? p.children() : b.getChildren(),
                p.pets() != null ? p.pets() : b.getPets(),
                p.expectedRent() != null ? p.expectedRent() : b.getExpectedRent(),
                mergeGuest(b.getGuest(), p.guest()));
    }

    private static Guest mergeGuest(Guest current, Guest p) {
        if (p == null) {
            return current;
        }
        return new Guest(or(p.name(), current.name()), or(p.street(), current.street()),
                or(p.postalCode(), current.postalCode()), or(p.city(), current.city()),
                or(p.country(), current.country()), or(p.phone(), current.phone()), or(p.email(), current.email()));
    }

    private static String or(String a, String b) {
        return a != null ? a : b;
    }

    /** Booking input from a mail; an existing booking keeps its number, channel and agency. */
    private static BookingService.BookingInput input(Long unitId, ParsedMail m, Booking existing) {
        return new BookingService.BookingInput(unitId, m.arrival(), m.departure(),
                existing != null ? existing.getExternalRef() : m.bookingRef(),
                existing != null ? existing.getChannel() : null,
                m.adults() != null ? m.adults() : 1, m.children(), m.expectedRent(),
                m.guest() == null ? Guest.EMPTY : m.guest(),
                existing != null && existing.getAgency() != null ? existing.getAgency() : m.agency(),
                m.pets());
    }
}
