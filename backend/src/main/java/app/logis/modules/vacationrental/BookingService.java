package app.logis.modules.vacationrental;

import app.logis.core.documents.Documents;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitSummary;
import app.logis.core.realestate.UnitUsage;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Bookings on vacation rental units. Lists are filtered and sorted in memory: a user sees a few hundred bookings
 * per year at most, and unit names live in the core.
 */
@Service
@Transactional
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingService {

    static final String NOT_FOUND = "Buchung nicht gefunden";
    static final String UNIT_NOT_FOUND = "Einheit nicht gefunden";
    static final String OVERLAP = "Die Einheit ist in diesem Zeitraum bereits belegt";

    private final BookingRepository bookings;
    private final StatementLineRepository lines;
    private final RentalAccess access;
    private final Documents documents;
    private final ApplicationEventPublisher events;
    private final AgencyService agencies;
    private final Clock clock;

    BookingService(BookingRepository bookings, StatementLineRepository lines, RentalAccess access,
            Documents documents, ApplicationEventPublisher events, AgencyService agencies, Clock clock) {
        this.bookings = bookings;
        this.lines = lines;
        this.access = access;
        this.documents = documents;
        this.events = events;
        this.agencies = agencies;
        this.clock = clock;
    }

    record BookingInput(Long unitId, LocalDate arrival, LocalDate departure, String externalRef, Integer adults,
            Integer children, BigDecimal expectedRent, Guest guest, Long agencyId, Integer pets) {
    }

    /** A booking as the user may see it: contact data only from EDITOR on; {@code agency} may be null. */
    record BookingDetails(Booking booking, UnitSummary unit, ShareRole role, Long statementId, Agency agency) {

        BookingStatus status() {
            return booking.isCancelled() ? BookingStatus.CANCELLED
                    : statementId != null ? BookingStatus.SETTLED : BookingStatus.PLANNED;
        }

        boolean canEdit() {
            return RentalAccess.canEdit(role);
        }

        Guest guest() {
            return canEdit() ? booking.getGuest() : booking.getGuest().nameOnly();
        }
    }

    enum SortKey {
        ARRIVAL, DEPARTURE, UNIT, GUEST, NIGHTS, EXPECTED_RENT, STATUS, AGENCY
    }

    /** {@code review = true}: only bookings and changes awaiting review. */
    record Filter(Long propertyId, Long unitId, BookingStatus status, LocalDate from, LocalDate to, String q,
            boolean review, Long agencyId) {
    }

    record ListResult(List<BookingDetails> items, int page, int size, long totalItems) {
    }

    // --- Reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    BookingDetails get(Long userId, Long id) {
        var booking = bookings.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var role = access.require(booking.getUnitId(), userId, ShareRole.VIEWER, NOT_FOUND);
        return details(List.of(booking), Map.of(booking.getUnitId(), role)).getFirst();
    }

    @Transactional(readOnly = true)
    ListResult list(Long userId, Filter filter, SortKey sort, boolean ascending, int page, int size) {
        var roles = access.visibleUnits(userId);
        if (roles.isEmpty()) {
            return new ListResult(List.of(), page, size, 0);
        }
        var summaries = access.summaries(roles.keySet());
        var unitIds = roles.keySet().stream()
                .filter(id -> filter.unitId() == null || filter.unitId().equals(id))
                .filter(id -> filter.propertyId() == null
                        || summaries.containsKey(id) && filter.propertyId().equals(summaries.get(id).propertyId()))
                .toList();
        if (unitIds.isEmpty()) {
            return new ListResult(List.of(), page, size, 0);
        }
        var q = filter.q() == null || filter.q().isBlank() ? null : filter.q().trim().toLowerCase(Locale.ROOT);
        var all = details(bookings.findForUnits(unitIds, filter.from(), filter.to()), roles).stream()
                .filter(d -> filter.status() == null || d.status() == filter.status())
                .filter(d -> !filter.review() || d.booking().getReviewState() != ReviewState.NONE)
                .filter(d -> filter.agencyId() == null || filter.agencyId().equals(d.booking().getAgencyId()))
                .filter(d -> q == null || matches(d, q))
                .sorted(comparator(sort, ascending))
                .toList();
        var from = Math.min(page * size, all.size());
        return new ListResult(all.subList(from, Math.min(from + size, all.size())), page, size, all.size());
    }

    /** Vacation rental units visible to the user, with role. */
    @Transactional(readOnly = true)
    List<Map.Entry<UnitSummary, ShareRole>> units(Long userId) {
        var roles = access.visibleUnits(userId);
        return access.summaries(roles.keySet()).values().stream()
                .filter(u -> u.usage() == UnitUsage.VACATION_RENTAL)
                .sorted(Comparator.comparing((UnitSummary u) -> u.propertyName().toLowerCase())
                        .thenComparing(u -> u.name().toLowerCase()))
                .map(u -> Map.entry(u, roles.get(u.id())))
                .toList();
    }

    /** Details of the given bookings for a user who has the given roles on their units. */
    @Transactional(readOnly = true)
    List<BookingDetails> details(Collection<Long> bookingIds, Map<Long, ShareRole> roles) {
        return details(bookings.findAllById(bookingIds), roles);
    }

    // --- Writing -------------------------------------------------------------------------------

    BookingDetails create(Long userId, BookingInput input) {
        var booking = new Booking(clock.instant());
        apply(userId, booking, input);
        return get(userId, save(booking).getId());
    }

    BookingDetails update(Long userId, Long id, BookingInput input, Long version) {
        var booking = requireEditable(userId, id);
        if (version == null || version != booking.getVersion()) {
            throw ApiException.conflict("Die Buchung wurde inzwischen geändert");
        }
        if (!booking.getUnitId().equals(input.unitId()) && settled(id)) {
            throw ApiException.badRequest("Die Einheit einer abgerechneten Buchung kann nicht geändert werden.");
        }
        apply(userId, booking, input);
        save(booking);
        return get(userId, id);
    }

    BookingDetails setCancelled(Long userId, Long id, boolean cancelled) {
        var booking = requireEditable(userId, id);
        if (!cancelled && booking.isCancelled()
                && bookings.overlaps(booking.getUnitId(), booking.getArrival(), booking.getDeparture(), id)) {
            throw ApiException.conflict(OVERLAP);
        }
        booking.setCancelled(cancelled);
        save(booking);
        return get(userId, id);
    }

    void delete(Long userId, Long id) {
        var booking = requireEditable(userId, id);
        if (settled(id)) {
            throw ApiException.conflict("Die Buchung ist abgerechnet und kann nur storniert werden");
        }
        documents.deleteAll(Booking.DOCUMENT_TYPE, id);
        bookings.delete(booking);
    }

    BookingDetails deleteGuest(Long userId, Long id) {
        requireEditable(userId, id).deleteGuest();
        return get(userId, id);
    }

    // --- Mail import (acting as the mailbox owner) -----------------------------------------------

    /** Creates a booking awaiting review; the usual rules apply, with the owner's rights. */
    BookingDetails createImported(Long ownerId, BookingInput input) {
        var booking = new Booking(clock.instant());
        booking.setReviewState(ReviewState.NEW);
        apply(ownerId, booking, input);
        return get(ownerId, save(booking).getId());
    }

    /** A later mail completes a booking that still awaits review (e.g. the rent from the confirmation). */
    void updateImported(Long ownerId, Long id, BookingInput input) {
        var booking = requireEditable(ownerId, id);
        apply(ownerId, booking, input);
        save(booking);
    }

    void propose(Long ownerId, Long id, BookingProposal proposal) {
        requireEditable(ownerId, id).setProposal(proposal);
    }

    /** A booking entered by hand gets the agency of the first mail that matches it. */
    void adoptAgency(Long ownerId, Long id, Long agencyId) {
        var booking = requireEditable(ownerId, id);
        if (booking.getAgencyId() == null) {
            booking.setAgencyId(agencyId);
        }
    }

    /** Bookings with this number on units the owner can see: of this agency first, then without agency. */
    @Transactional(readOnly = true)
    List<Booking> findByRef(Long ownerId, Long agencyId, String ref) {
        var units = access.visibleUnits(ownerId).keySet();
        return units.isEmpty() ? List.of() : bookings.findByRef(units, agencyId, ref);
    }

    /** The booking that is not cancelled with exactly this period on a unit the owner can edit. */
    @Transactional(readOnly = true)
    Optional<Booking> findSamePeriod(Long ownerId, Long unitId, LocalDate arrival, LocalDate departure) {
        if (!RentalAccess.canEdit(access.role(unitId, ownerId).orElse(null))) {
            return Optional.empty();
        }
        return bookings.findByUnitIdAndArrivalAndDepartureAndCancelledFalse(unitId, arrival, departure).stream()
                .findFirst();
    }

    // --- Review of imported bookings -----------------------------------------------------------

    /** Confirms a booking that came from a mail; it becomes an ordinary booking. */
    BookingDetails confirm(Long userId, Long id) {
        var booking = requireEditable(userId, id);
        if (booking.getReviewState() == ReviewState.NEW) {
            booking.setReviewState(booking.getProposal() == null ? ReviewState.NONE : ReviewState.CHANGE);
        }
        return get(userId, id);
    }

    /** Discards a booking that came from a mail and has not been confirmed: it is deleted with its documents. */
    void discard(Long userId, Long id) {
        var booking = requireEditable(userId, id);
        if (booking.getReviewState() != ReviewState.NEW) {
            throw ApiException.conflict("Nur Buchungen „zu prüfen“ können verworfen werden.");
        }
        if (settled(id)) {
            throw ApiException.conflict("Die Buchung ist abgerechnet und kann nur storniert werden");
        }
        documents.deleteAll(Booking.DOCUMENT_TYPE, id);
        bookings.delete(booking);
        events.publishEvent(new ImportedBookingDiscarded(id));
    }

    /** Applies the proposed change or cancellation under the usual booking rules. */
    BookingDetails applyProposal(Long userId, Long id) {
        var booking = requireEditable(userId, id);
        var p = booking.getProposal();
        if (p == null) {
            throw ApiException.conflict("Es gibt keine vorgeschlagene Änderung.");
        }
        if (p.type() == BookingProposal.Type.CANCELLATION) {
            booking.setCancelled(true);
        } else {
            var g = p.guest() == null ? booking.getGuest() : merge(booking.getGuest(), p.guest());
            apply(userId, booking, new BookingInput(booking.getUnitId(),
                    p.arrival() != null ? p.arrival() : booking.getArrival(),
                    p.departure() != null ? p.departure() : booking.getDeparture(),
                    booking.getExternalRef(),
                    p.adults() != null ? p.adults() : booking.getAdults(),
                    p.children() != null ? p.children() : booking.getChildren(),
                    p.expectedRent() != null ? p.expectedRent() : booking.getExpectedRent(), g,
                    booking.getAgencyId(), p.pets() != null ? p.pets() : booking.getPets()));
        }
        booking.setProposal(null);
        save(booking);
        return get(userId, id);
    }

    BookingDetails discardProposal(Long userId, Long id) {
        requireEditable(userId, id).setProposal(null);
        return get(userId, id);
    }

    /** Proposed guest fields replace only what the mail carries. */
    private static Guest merge(Guest current, Guest proposed) {
        return new Guest(or(proposed.name(), current.name()), or(proposed.street(), current.street()),
                or(proposed.postalCode(), current.postalCode()), or(proposed.city(), current.city()),
                or(proposed.country(), current.country()), or(proposed.phone(), current.phone()),
                or(proposed.email(), current.email()));
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Document access: reading needs EDITOR too, because documents usually contain contact data. */
    @Transactional(readOnly = true)
    boolean canEditBooking(Long id, Long userId) {
        return bookings.findById(id)
                .flatMap(b -> access.role(b.getUnitId(), userId))
                .map(RentalAccess::canEdit)
                .orElse(false);
    }

    // --- Helpers -------------------------------------------------------------------------------

    private Booking requireEditable(Long userId, Long id) {
        var booking = bookings.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        access.require(booking.getUnitId(), userId, ShareRole.EDITOR, NOT_FOUND);
        return booking;
    }

    private boolean settled(Long bookingId) {
        return lines.existsById(bookingId);
    }

    private void apply(Long userId, Booking booking, BookingInput input) {
        access.require(input.unitId(), userId, ShareRole.EDITOR, UNIT_NOT_FOUND);
        var unit = access.summaries(List.of(input.unitId())).get(input.unitId());
        var unitChanged = !input.unitId().equals(booking.getUnitId());
        if (unitChanged || booking.getId() == null) {
            if (unit.usage() != UnitUsage.VACATION_RENTAL) {
                throw ApiException.badRequest("Die Einheit wird nicht als Ferienwohnung genutzt");
            }
            if (unit.archived() || unit.propertyArchived()) {
                throw ApiException.badRequest("Die Einheit ist archiviert");
            }
        }
        if (input.arrival() == null || input.departure() == null) {
            throw ApiException.badRequest("Bitte An- und Abreise angeben.");
        }
        if (!input.departure().isAfter(input.arrival())) {
            throw ApiException.badRequest("Die Abreise muss nach der Anreise liegen.");
        }
        var adults = input.adults() == null ? 0 : input.adults();
        var children = input.children() == null ? 0 : input.children();
        if (adults < 1 || adults > 99) {
            throw ApiException.badRequest("Bitte mindestens einen Erwachsenen angeben.");
        }
        if (children < 0 || children > 99) {
            throw ApiException.badRequest("Die Zahl der Kinder ist ungültig.");
        }
        var ref = text(input.externalRef(), 64, "Die Buchungsnummer");
        var agencyId = input.agencyId();
        if (agencyId != null && !agencyId.equals(booking.getAgencyId())) {
            agencies.require(agencyId);
        }
        var pets = input.pets() == null ? 0 : input.pets();
        if (pets < 0 || pets > 20) {
            throw ApiException.badRequest("Die Zahl der Haustiere ist ungültig.");
        }
        if (ref != null && bookings.refTaken(input.unitId(), agencyId, ref, booking.getId())) {
            throw ApiException.conflict("Diese Buchungsnummer ist bereits erfasst");
        }
        if (!booking.isCancelled()
                && bookings.overlaps(input.unitId(), input.arrival(), input.departure(), booking.getId())) {
            throw ApiException.conflict(OVERLAP);
        }
        booking.apply(input.unitId(), input.arrival(), input.departure(), ref, adults, children,
                Money.optional(input.expectedRent()));
        booking.setAgencyId(agencyId);
        booking.setPets(pets);
        var g = input.guest() == null ? Guest.EMPTY : input.guest();
        booking.setGuest(new Guest(text(g.name(), 128, "Der Name"), text(g.street(), 128, "Die Straße"),
                text(g.postalCode(), 16, "Die PLZ"), text(g.city(), 128, "Der Ort"),
                text(g.country(), 64, "Das Land"), text(g.phone(), 64, "Die Telefonnummer"),
                text(g.email(), 254, "Die E-Mail-Adresse")));
    }

    /** Saves and turns a race on the overlap constraint into the user message. */
    private Booking save(Booking booking) {
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("bookings_no_overlap")) {
                throw ApiException.conflict(OVERLAP);
            }
            throw e;
        }
    }

    private List<BookingDetails> details(List<Booking> list, Map<Long, ShareRole> roles) {
        if (list.isEmpty()) {
            return List.of();
        }
        var summaries = access.summaries(list.stream().map(Booking::getUnitId).distinct().toList());
        var statementByBooking = lines.findByBookingIdIn(list.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(StatementLine::getBookingId, StatementLine::getStatementId));
        var agencyById = agencies.byIds(list.stream().map(Booking::getAgencyId).filter(java.util.Objects::nonNull)
                .distinct().toList());
        return list.stream()
                .map(b -> new BookingDetails(b, summaries.get(b.getUnitId()), roles.get(b.getUnitId()),
                        statementByBooking.get(b.getId()), b.getAgencyId() == null ? null
                                : agencyById.get(b.getAgencyId())))
                .toList();
    }

    private static boolean matches(BookingDetails d, String q) {
        var ref = d.booking().getExternalRef();
        var name = d.booking().getGuest().name();
        return ref != null && ref.toLowerCase(Locale.ROOT).contains(q)
                || name != null && name.toLowerCase(Locale.ROOT).contains(q);
    }

    private static Comparator<BookingDetails> comparator(SortKey sort, boolean ascending) {
        Comparator<BookingDetails> c = switch (sort) {
            case ARRIVAL -> Comparator.comparing(d -> d.booking().getArrival());
            case DEPARTURE -> Comparator.comparing(d -> d.booking().getDeparture());
            case UNIT -> Comparator.comparing(d -> (d.unit().propertyName() + " " + d.unit().name())
                    .toLowerCase(Locale.ROOT));
            case GUEST -> Comparator.comparing(d -> d.booking().getGuest().name() == null ? ""
                    : d.booking().getGuest().name().toLowerCase(Locale.ROOT));
            case NIGHTS -> Comparator.comparingLong(d -> d.booking().nights());
            case EXPECTED_RENT -> Comparator.comparing(d -> d.booking().getExpectedRent() == null ? BigDecimal.ZERO
                    : d.booking().getExpectedRent());
            case STATUS -> Comparator.comparing(BookingDetails::status);
            case AGENCY -> Comparator.comparing(d -> d.agency() == null ? ""
                    : d.agency().getName().toLowerCase(Locale.ROOT));
        };
        if (!ascending) {
            c = c.reversed();
        }
        return c.thenComparing(d -> d.booking().getId());
    }

    private static String text(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var clean = value.trim();
        if (clean.length() > maxLength) {
            throw ApiException.badRequest(field + " darf höchstens " + maxLength + " Zeichen lang sein.");
        }
        return clean;
    }
}
