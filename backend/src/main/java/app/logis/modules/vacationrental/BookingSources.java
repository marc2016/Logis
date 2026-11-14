package app.logis.modules.vacationrental;

import app.logis.core.documents.DocumentDeleted;
import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Sources of bookings and the locations of their field values (design.md D6, D7). Rights are checked by the
 * callers: the import acts for the mailbox owner, the booking service for the editing user.
 */
@Service
@Transactional
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingSources {

    static final String MAIL = "MAIL";

    private final BookingSourceStore store;

    BookingSources(BookingSourceStore store) {
        this.store = store;
    }

    /** Stores the text a mail was read from as a source of the booking. */
    Long addMail(Long bookingId, Long documentId, String label, MailContent mail) {
        return store.insert(bookingId, documentId, MAIL, label, mail.sender(), mail.receivedAt(),
                mail.businessSubject(), mail.text());
    }

    /** The mail's locations become the current ones for these fields. */
    void setCurrent(Long bookingId, Long sourceId, ParsedMail mail, Collection<BookingField> fields) {
        for (var field : fields) {
            var at = mail.locations().get(field);
            if (at != null) {
                store.put(bookingId, BookingSourceStore.Scope.CURRENT, field, sourceId, at, FieldValues.of(mail, field));
            }
        }
    }

    /** The mail's locations for fields that have none yet; existing locations stay. */
    void fillCurrent(Long bookingId, Long sourceId, ParsedMail mail, Collection<BookingField> fields) {
        for (var field : fields) {
            var at = mail.locations().get(field);
            if (at != null) {
                store.putIfAbsent(bookingId, BookingSourceStore.Scope.CURRENT, field, sourceId, at,
                        FieldValues.of(mail, field));
            }
        }
    }

    /** The locations of a new proposal; they replace those of an older one. */
    void setProposal(Long bookingId, Long sourceId, ParsedMail mail, Collection<BookingField> fields) {
        store.deleteScope(bookingId, BookingSourceStore.Scope.PROPOSAL);
        for (var field : fields) {
            var at = mail.locations().get(field);
            if (at != null) {
                store.put(bookingId, BookingSourceStore.Scope.PROPOSAL, field, sourceId, at,
                        FieldValues.of(mail, field));
            }
        }
    }

    /** The proposal was applied: its locations become the current ones of the fields it changed. */
    void proposalApplied(Long bookingId, BookingProposal proposal) {
        store.promote(bookingId, fields(proposal));
    }

    /** The proposal was discarded or replaced: its locations go. */
    void proposalDropped(Long bookingId) {
        store.deleteScope(bookingId, BookingSourceStore.Scope.PROPOSAL);
    }

    /** Guest data deleted: the locations of the guest fields go with their mail values; sources stay. */
    void guestDeleted(Long bookingId) {
        store.deleteFields(bookingId, Arrays.stream(BookingField.values()).filter(BookingField::isGuest).toList());
    }

    /** A deleted mail document takes its source along, and with it the locations in it (design.md D9). */
    @EventListener
    void onDocumentDeleted(DocumentDeleted event) {
        if (Booking.DOCUMENT_TYPE.equals(event.ownerType())) {
            store.deleteByDocument(event.documentId());
        }
    }

    record SourceRef(Long id, String kind, String label, java.time.Instant receivedAt) {
    }

    /** A field's location; {@code differs} when the booking's value no longer matches the source. */
    record LocationView(Long sourceId, Location.Part part, int start, int end, String sourceValue, boolean differs) {
    }

    record SourcesView(java.util.List<SourceRef> sources, java.util.Map<String, LocationView> locations,
            java.util.Map<String, LocationView> proposalLocations) {

        static final SourcesView NONE = new SourcesView(java.util.List.of(), java.util.Map.of(), java.util.Map.of());
    }

    record SourceText(Long id, String label, String sender, java.time.Instant receivedAt, String subject,
            String text) {
    }

    /** Sources and locations of a booking, for users with at least EDITOR (the caller checks). */
    @Transactional(readOnly = true)
    SourcesView view(Booking booking) {
        var id = booking.getId();
        var refs = store.list(id).stream()
                .map(src -> new SourceRef(src.id(), src.kind(), src.label(), src.receivedAt())).toList();
        var current = new java.util.LinkedHashMap<String, LocationView>();
        store.locations(id, BookingSourceStore.Scope.CURRENT).forEach((field, l) -> current.put(field.key(),
                new LocationView(l.sourceId(), l.location().part(), l.location().start(), l.location().end(),
                        l.sourceValue(), !FieldValues.same(field, FieldValues.of(booking, field), l.sourceValue()))));
        var proposal = new java.util.LinkedHashMap<String, LocationView>();
        store.locations(id, BookingSourceStore.Scope.PROPOSAL).forEach((field, l) -> proposal.put(field.key(),
                new LocationView(l.sourceId(), l.location().part(), l.location().start(), l.location().end(),
                        l.sourceValue(), false)));
        return new SourcesView(refs, current, proposal);
    }

    @Transactional(readOnly = true)
    java.util.Optional<SourceText> text(Long bookingId, Long sourceId) {
        return store.get(bookingId, sourceId).map(src -> new SourceText(src.id(), src.label(), src.sender(),
                src.receivedAt(), src.subject(), src.text()));
    }

    /** All fields a mail carries a value for. */
    static Set<BookingField> fields(ParsedMail mail) {
        var result = EnumSet.noneOf(BookingField.class);
        for (var field : BookingField.values()) {
            if (FieldValues.of(mail, field) != null) {
                result.add(field);
            }
        }
        return result;
    }

    /** The fields a proposal changes; none for a cancellation. */
    static Set<BookingField> fields(BookingProposal p) {
        var result = EnumSet.noneOf(BookingField.class);
        if (p == null || p.type() == BookingProposal.Type.CANCELLATION) {
            return result;
        }
        add(result, BookingField.ARRIVAL, p.arrival());
        add(result, BookingField.DEPARTURE, p.departure());
        add(result, BookingField.ADULTS, p.adults());
        add(result, BookingField.CHILDREN, p.children());
        add(result, BookingField.PETS, p.pets());
        add(result, BookingField.EXPECTED_RENT, p.expectedRent());
        var g = p.guest();
        if (g != null) {
            add(result, BookingField.GUEST_NAME, g.name());
            add(result, BookingField.GUEST_STREET, g.street());
            add(result, BookingField.GUEST_POSTAL_CODE, g.postalCode());
            add(result, BookingField.GUEST_CITY, g.city());
            add(result, BookingField.GUEST_COUNTRY, g.country());
            add(result, BookingField.GUEST_PHONE, g.phone());
            add(result, BookingField.GUEST_EMAIL, g.email());
        }
        return result;
    }

    private static void add(Set<BookingField> fields, BookingField field, Object value) {
        if (value != null) {
            fields.add(field);
        }
    }
}
