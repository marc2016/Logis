package app.logis.modules.vacationrental;

import app.logis.core.documents.Documents;
import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
    private final AgencyService agencies;
    private final BookingSources sources;

    ImportEngine(BookingService bookings, UnitMappingRepository mappings, RentalAccess access, Documents documents,
            AgencyService agencies, BookingSources sources) {
        this.bookings = bookings;
        this.mappings = mappings;
        this.access = access;
        this.documents = documents;
        this.agencies = agencies;
        this.sources = sources;
    }

    /** The mail as it was recognised: the parsed values, the text they were read from and the raw mail. */
    record Mail(ParsedMail parsed, MailContent content, byte[] raw) {
    }

    Outcome apply(Long ownerId, ParsedMail mail, MailContent content, byte[] raw) {
        return apply(ownerId, new Mail(mail, content, raw));
    }

    private Outcome apply(Long ownerId, Mail in) {
        var mail = in.parsed();
        // The mail's agency name, resolved through the shared list (an unknown name creates the agency).
        var agencyId = agencies.resolve(mail.agency()).map(Agency::getId).orElse(null);
        if (mail.type() == ParsedMail.MailType.BLOCK_NOTICE) {
            return blockNotice(ownerId, agencyId, in);
        }
        if (mail.type() == ParsedMail.MailType.OWNER_BLOCK) {
            return ownerBlock(ownerId, agencyId, in);
        }
        if (mail.type() == ParsedMail.MailType.EXTENSION) {
            return extension(ownerId, agencyId, in);
        }
        var existing = bookings.findByRef(ownerId, agencyId, mail.bookingRef()).stream().findFirst();
        try {
            return switch (mail.type()) {
                case CONFIRMATION, SHORT_NOTICE -> existing.isPresent()
                        ? update(ownerId, agencyId, existing.get(), in)
                        : create(ownerId, agencyId, in);
                case CHANGE -> existing.isPresent()
                        ? update(ownerId, agencyId, existing.get(), in)
                        : new Outcome(BookingMailResult.NOT_MATCHED, null, "Buchung " + mail.bookingRef()
                                + " ist nicht erfasst");
                case CANCELLATION -> existing.isPresent()
                        ? cancel(ownerId, agencyId, existing.get(), in)
                        : new Outcome(BookingMailResult.NOT_MATCHED, null, "Buchung " + mail.bookingRef()
                                + " ist nicht erfasst");
                case BLOCK_NOTICE, OWNER_BLOCK, EXTENSION -> throw new IllegalStateException("handled above");
            };
        } catch (ApiException e) {
            return new Outcome(BookingMailResult.FAILED, existing.map(Booking::getId).orElse(null), e.getMessage());
        }
    }

    /**
     * The agency only blocked its calendar for a stay booked elsewhere: the number is the agency's, the booking
     * carries the intermediary's, so the stay is found by its exact period (design.md D3). Never creates a booking.
     */
    private Outcome blockNotice(Long ownerId, Long agencyId, Mail in) {
        var mail = in.parsed();
        var unit = editableUnit(ownerId, agencyId, mail);
        if (unit.outcome() != null) {
            return unit.outcome();
        }
        var booking = bookings.findSamePeriod(ownerId, unit.unitId(), mail.arrival(), mail.departure());
        if (booking.isEmpty()) {
            return new Outcome(BookingMailResult.NOT_MATCHED, null, "Keine Buchung vom "
                    + GermanText.format(mail.arrival()) + " bis " + GermanText.format(mail.departure()));
        }
        var id = booking.get().getId();
        attach(id, in);
        return new Outcome(BookingMailResult.ATTACHED, id,
                mail.intermediary() == null ? "Blockung" : "Blockung durch " + mail.intermediary());
    }

    /**
     * The owner blocked dates for their own use: an owner use with the agency's number is created without review,
     * or moved to the mail's period; an owner use recorded by hand with the same period takes the number.
     */
    private Outcome ownerBlock(Long ownerId, Long agencyId, Mail in) {
        var mail = in.parsed();
        var unit = editableUnit(ownerId, agencyId, mail);
        if (unit.outcome() != null) {
            return unit.outcome();
        }
        var existing = bookings.findByRef(ownerId, agencyId, mail.bookingRef()).stream().findFirst();
        try {
            if (existing.isPresent()) {
                var booking = existing.get();
                if (!booking.isOwnerUse()) {
                    return new Outcome(BookingMailResult.FAILED, booking.getId(),
                            "Die Buchungsnummer gehört zu einer Gastbuchung");
                }
                var moved = !booking.getArrival().equals(mail.arrival())
                        || !booking.getDeparture().equals(mail.departure());
                if (moved) {
                    bookings.moveOwnerUse(ownerId, booking.getId(), mail.arrival(), mail.departure());
                }
                attach(booking.getId(), in);
                return new Outcome(BookingMailResult.ATTACHED, booking.getId(),
                        moved ? "Eigenbelegung verschoben" : "Eigenbelegung");
            }
            var byHand = bookings.findSamePeriod(ownerId, unit.unitId(), mail.arrival(), mail.departure())
                    .filter(b -> b.isOwnerUse() && b.getExternalRef() == null);
            if (byHand.isPresent()) {
                var id = byHand.get().getId();
                bookings.identifyOwnerUse(ownerId, id, mail.bookingRef(), agencyId);
                attach(id, in);
                return new Outcome(BookingMailResult.ATTACHED, id, "Eigenbelegung");
            }
            var created = bookings.createOwnerUse(ownerId, BookingService.BookingInput.ownerUse(unit.unitId(),
                    mail.arrival(), mail.departure(), mail.bookingRef(), agencyId));
            var id = created.booking().getId();
            attach(id, in);
            return new Outcome(BookingMailResult.OWNER_USE_CREATED, id, null);
        } catch (ApiException e) {
            if (BookingService.OVERLAP.equals(e.getMessage())) {
                return new Outcome(BookingMailResult.PERIOD_TAKEN, null, e.getMessage());
            }
            return new Outcome(BookingMailResult.FAILED, existing.map(Booking::getId).orElse(null), e.getMessage());
        }
    }

    /**
     * The agency extended a stay (design D3 of fewo-extension-mails): the guest booking with exactly the original
     * period gets the extended period, proposed for review or taken over while it is not reviewed yet.
     */
    private Outcome extension(Long ownerId, Long agencyId, Mail in) {
        var mail = in.parsed();
        var unit = editableUnit(ownerId, agencyId, mail);
        if (unit.outcome() != null) {
            return unit.outcome();
        }
        var original = mail.original();
        var booking = bookings.findSamePeriod(ownerId, unit.unitId(), original.arrival(), original.departure())
                .filter(b -> !b.isOwnerUse());
        if (booking.isPresent()) {
            try {
                return update(ownerId, agencyId, booking.get(), in);
            } catch (ApiException e) {
                return new Outcome(BookingMailResult.FAILED, booking.get().getId(), e.getMessage());
            }
        }
        var extended = bookings.findSamePeriod(ownerId, unit.unitId(), mail.arrival(), mail.departure())
                .filter(b -> !b.isOwnerUse());
        if (extended.isPresent()) {
            var id = extended.get().getId();
            attach(id, in);
            return new Outcome(BookingMailResult.ATTACHED, id, "Verlängerung bereits erfasst");
        }
        return new Outcome(BookingMailResult.NOT_MATCHED, null, "Keine Buchung vom "
                + GermanText.format(original.arrival()) + " bis " + GermanText.format(original.departure()));
    }

    private record UnitLookup(Long unitId, Outcome outcome) {
    }

    /** The unit the mail's property is mapped to, if the owner may edit it; otherwise the inbox outcome. */
    private UnitLookup editableUnit(Long ownerId, Long agencyId, ParsedMail mail) {
        var mapping = mail.propertyName() == null || agencyId == null ? null
                : mappings.find(ownerId, agencyId, mail.propertyName()).orElse(null);
        if (mapping == null) {
            return new UnitLookup(null, new Outcome(BookingMailResult.UNASSIGNED_UNIT, null,
                    "Einheit für „" + Objects.toString(mail.propertyName(), "?") + "“ zuordnen"));
        }
        var role = access.role(mapping.getUnitId(), ownerId).orElse(null);
        if (!RentalAccess.canEdit(role)) {
            return new UnitLookup(null, new Outcome(BookingMailResult.UNASSIGNED_UNIT, null,
                    "Keine Bearbeitungsrechte für die zugeordnete Einheit"));
        }
        return new UnitLookup(mapping.getUnitId(), null);
    }

    private Outcome create(Long ownerId, Long agencyId, Mail in) {
        var mail = in.parsed();
        var unit = editableUnit(ownerId, agencyId, mail);
        if (unit.outcome() != null) {
            return unit.outcome();
        }
        try {
            var created = bookings.createImported(ownerId, input(unit.unitId(), agencyId, mail, null));
            var id = created.booking().getId();
            sources.setCurrent(id, attach(id, in), mail, BookingSources.fields(mail));
            return new Outcome(BookingMailResult.BOOKING_CREATED, created.booking().getId(), null);
        } catch (ApiException e) {
            if (BookingService.OVERLAP.equals(e.getMessage())) {
                return new Outcome(BookingMailResult.PERIOD_TAKEN, null, e.getMessage());
            }
            throw e;
        }
    }

    private Outcome update(Long ownerId, Long agencyId, Booking booking, Mail in) {
        var mail = in.parsed();
        var id = booking.getId();
        bookings.adoptAgency(ownerId, id, agencyId);
        var proposal = differences(booking, mail);
        var carried = BookingSources.fields(mail);
        if (proposal == null) {
            sources.fillCurrent(id, attach(id, in), mail, carried);
            return new Outcome(BookingMailResult.ATTACHED, id, null);
        }
        var changed = BookingSources.fields(proposal);
        var unchanged = carried.stream().filter(f -> !changed.contains(f)).toList();
        if (booking.getReviewState() == ReviewState.NEW) {
            // Not confirmed yet: the mail simply completes the prepared booking.
            bookings.updateImported(ownerId, id, input(booking.getUnitId(), agencyId, merged(booking, proposal),
                    booking));
            var source = attach(id, in);
            sources.setCurrent(id, source, mail, changed);
            sources.fillCurrent(id, source, mail, unchanged);
            return new Outcome(BookingMailResult.ATTACHED, id, "Angaben ergänzt");
        }
        bookings.propose(ownerId, id, proposal);
        var source = attach(id, in);
        sources.setProposal(id, source, mail, changed);
        sources.fillCurrent(id, source, mail, unchanged);
        return new Outcome(BookingMailResult.CHANGE_PROPOSED, id, null);
    }

    private Outcome cancel(Long ownerId, Long agencyId, Booking booking, Mail in) {
        var id = booking.getId();
        bookings.adoptAgency(ownerId, id, agencyId);
        var source = attach(id, in);
        if (booking.isCancelled()) {
            return new Outcome(BookingMailResult.ATTACHED, id, "Bereits storniert");
        }
        bookings.propose(ownerId, id, BookingProposal.cancellation(null));
        sources.setProposal(id, source, in.parsed(), java.util.Set.of());
        return new Outcome(BookingMailResult.CHANGE_PROPOSED, id, "Stornierung");
    }

    /** Stores the raw mail as a document of the booking and its text as a source; returns the source. */
    private Long attach(Long bookingId, Mail in) {
        var mail = in.parsed();
        var label = label(mail.type());
        // An extension notice carries no booking number.
        var name = mail.agency() + " " + label + (mail.bookingRef() == null ? "" : " " + mail.bookingRef());
        var documentId = documents.store(Booking.DOCUMENT_TYPE, bookingId, name + ".eml", "message/rfc822", in.raw());
        return sources.addMail(bookingId, documentId, mail.agency() + " " + label, in.content());
    }

    /** The kind of mail in document names and source labels. */
    static String label(ParsedMail.MailType type) {
        return switch (type) {
            case CONFIRMATION -> "Buchungsbestätigung";
            case SHORT_NOTICE -> "Last-Minute";
            case CHANGE -> "Änderung";
            case CANCELLATION -> "Stornierung";
            case BLOCK_NOTICE -> "Blockung";
            case OWNER_BLOCK -> "Eigentümerblockung";
            case EXTENSION -> "Verlängerung";
        };
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
        var name = mail.name() != null && !FieldValues.sameName(current.name(), mail.name()) ? mail.name() : null;
        var g = new Guest(name, differs(current.street(), mail.street()), differs(current.postalCode(),
                mail.postalCode()), differs(current.city(), mail.city()), differs(current.country(), mail.country()),
                FieldValues.samePhone(current.phone(), mail.phone()) ? null : differs(current.phone(), mail.phone()), differs(current.email(), mail.email()));
        return g.isEmpty() ? null : g;
    }

    private static String differs(String current, String mail) {
        if (mail == null || mail.isBlank()) {
            return null;
        }
        return current != null && current.strip().equalsIgnoreCase(mail.strip()) ? null : mail.strip();
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

    /** Booking input from a mail; an existing booking keeps its number and agency. */
    private static BookingService.BookingInput input(Long unitId, Long agencyId, ParsedMail m, Booking existing) {
        return new BookingService.BookingInput(unitId, m.arrival(), m.departure(),
                existing != null ? existing.getExternalRef() : m.bookingRef(),
                m.adults() != null ? m.adults() : 1, m.children(), m.expectedRent(),
                m.guest() == null ? Guest.EMPTY : m.guest(),
                existing != null && existing.getAgencyId() != null ? existing.getAgencyId() : agencyId,
                m.pets());
    }
}
