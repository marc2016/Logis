package app.logis.modules.longtermrental;

import app.logis.core.ledger.ManagedTransactions;
import app.logis.shared.GermanTime;
import app.logis.core.documents.Documents;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitAccess;
import app.logis.core.realestate.UnitSummary;
import app.logis.core.realestate.UnitUsage;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import app.logis.modules.longtermrental.TenancyStore.ItemKind;
import app.logis.modules.longtermrental.TenancyStore.RentItem;
import app.logis.modules.longtermrental.TenancyStore.RentTerm;
import app.logis.modules.longtermrental.TenancyStore.Tenancy;
import app.logis.modules.longtermrental.TenancyStore.Tenant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Long-term tenancies (spec tenancies; design.md of long-term-rental). Rights come from the effective role on the
 * unit: without a role a tenancy does not exist for the user, viewers read, editors change.
 */
@Service
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class TenancyService {

    static final String NOT_FOUND = "Mietverhältnis nicht gefunden";
    static final String UNIT_NOT_FOUND = "Einheit nicht gefunden";
    static final String FORBIDDEN = "Dafür fehlt die Berechtigung.";
    static final String WRONG_USAGE = "Die Einheit ist keine Dauermiete";
    static final String END_BEFORE_START = "Das Ende liegt vor dem Beginn";
    static final String OVERLAP = "Die Einheit ist in diesem Zeitraum bereits vermietet";
    static final String NO_TENANT = "Bitte mindestens einen Mieter mit Namen angeben.";
    static final String CHANGED = "Das Mietverhältnis wurde inzwischen geändert";
    static final String DUPLICATE_TERM = "Für dieses Datum gibt es bereits eine Miete";
    static final String FIRST_TERM = "Die erste Miete kann nicht gelöscht werden.";
    static final String TERM_BEFORE_START = "Die Miete muss nach dem Beginn des Mietverhältnisses gelten.";
    static final String TERM_BEFORE_NEW_START = "Eine spätere Miete gilt bereits vor dem neuen Beginn.";
    static final String HAS_PAYMENTS = "Das Mietverhältnis hat Zahlungen und kann nicht gelöscht werden";
    static final String NOT_ENDED = "Mieterdaten können erst nach dem Ende des Mietverhältnisses gelöscht werden.";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");

    enum State {
        FUTURE, RUNNING, ENDED
    }

    record OtherItem(String label, BigDecimal amount) {
    }

    record RentInput(LocalDate validFrom, BigDecimal coldRent, BigDecimal utilitiesAdvance, List<OtherItem> others) {
    }

    record TenancyInput(Long unitId, LocalDate startDate, LocalDate endDate, BigDecimal depositAmount,
            LocalDate depositReceivedOn, List<Tenant> tenants) {
    }

    /** {@code current}: the rent term valid today, or at the start or end for future and ended tenancies. */
    record Details(Tenancy tenancy, UnitSummary unit, ShareRole role, List<Tenant> tenants, List<RentTerm> terms,
            RentTerm current, State state, List<RentPaymentStore.Payment> payments, RentLedger.Account account) {

        boolean canEdit() {
            return role.atLeast(ShareRole.EDITOR);
        }
    }

    /** A long-term unit with its running tenancy, or since when it is vacant, and the next tenancy if any. */
    record UnitOverview(UnitSummary unit, ShareRole role, Details current, LocalDate vacantSince, Details next) {
    }

    private final TenancyStore store;
    private final UnitAccess units;
    private final Clock clock;
    private final Documents documents;
    private final RentPaymentStore payments;
    private final ManagedTransactions managed;

    TenancyService(TenancyStore store, UnitAccess units, Clock clock, Documents documents, RentPaymentStore payments,
            ManagedTransactions managed) {
        this.documents = documents;
        this.payments = payments;
        this.managed = managed;
        this.store = store;
        this.units = units;
        this.clock = clock;
    }

    // --- Reading ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<Details> list(Long userId, Long propertyId, Long unitId, Set<State> states) {
        var roles = units.units(userId, ShareRole.VIEWER);
        var summaries = units.summaries(roles.keySet());
        var visible = summaries.values().stream()
                .filter(u -> propertyId == null || u.propertyId().equals(propertyId))
                .filter(u -> unitId == null || u.id().equals(unitId))
                .map(UnitSummary::id).toList();
        return details(store.ofUnits(visible), summaries, roles).stream()
                .filter(d -> states == null || states.isEmpty() || states.contains(d.state()))
                .sorted(Comparator.comparing((Details d) -> d.unit().propertyName().toLowerCase())
                        .thenComparing(d -> d.unit().name().toLowerCase())
                        .thenComparing(d -> d.tenancy().startDate(), Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    Details get(Long userId, Long id) {
        var tenancy = store.byId(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var role = units.roleOn(tenancy.unitId(), userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        return details(List.of(tenancy), units.summaries(List.of(tenancy.unitId())), Map.of(tenancy.unitId(), role))
                .getFirst();
    }

    /** The visible units with the usage „Dauermiete“ that are not archived, with tenant or vacancy (design D4). */
    @Transactional(readOnly = true)
    List<UnitOverview> units(Long userId) {
        var roles = units.units(userId, ShareRole.VIEWER);
        var summaries = units.summaries(roles.keySet());
        var rental = summaries.values().stream()
                .filter(u -> u.usage() == UnitUsage.LONG_TERM_RENTAL && !u.archived() && !u.propertyArchived())
                .sorted(Comparator.comparing((UnitSummary u) -> u.propertyName().toLowerCase())
                        .thenComparing(u -> u.name().toLowerCase()))
                .toList();
        var all = details(store.ofUnits(rental.stream().map(UnitSummary::id).toList()), summaries, roles);
        var result = new ArrayList<UnitOverview>();
        for (var unit : rental) {
            var own = all.stream().filter(d -> d.unit().id().equals(unit.id())).toList();
            var current = own.stream().filter(d -> d.state() == State.RUNNING).findFirst().orElse(null);
            var next = own.stream().filter(d -> d.state() == State.FUTURE)
                    .min(Comparator.comparing(d -> d.tenancy().startDate())).orElse(null);
            var lastEnd = own.stream().filter(d -> d.state() == State.ENDED).map(d -> d.tenancy().endDate())
                    .max(Comparator.naturalOrder()).orElse(null);
            result.add(new UnitOverview(unit, roles.get(unit.id()), current,
                    current == null && lastEnd != null ? lastEnd.plusDays(1) : null, next));
        }
        return result;
    }

    /** For documents: whether the user may edit the tenancy. */
    @Transactional(readOnly = true)
    boolean canEdit(Long tenancyId, Long userId) {
        return store.byId(tenancyId).flatMap(t -> units.roleOn(t.unitId(), userId))
                .map(r -> r.atLeast(ShareRole.EDITOR)).orElse(false);
    }

    // --- Writing ----------------------------------------------------------------------------------

    @Transactional
    Details create(Long userId, TenancyInput input, RentInput rent) {
        var tenants = tenants(input.tenants());
        validatePeriod(input);
        requireRentalUnit(userId, input.unitId());
        var items = items(rent);
        if (store.overlaps(input.unitId(), input.startDate(), input.endDate(), null)) {
            throw ApiException.conflict(OVERLAP);
        }
        try {
            var id = store.insert(input.unitId(), input.startDate(), input.endDate(), amount(input.depositAmount(), true),
                    input.depositAmount() == null ? null : input.depositReceivedOn());
            store.replaceTenants(id, tenants);
            store.insertTerm(id, input.startDate(), items);
            return get(userId, id);
        } catch (DataIntegrityViolationException e) {
            // Two tenancies recorded at the same moment: the exclusion constraint decides.
            throw ApiException.conflict(OVERLAP);
        }
    }

    @Transactional
    Details update(Long userId, Long id, TenancyInput input, Long version) {
        var existing = requireEditable(userId, id);
        var tenancy = existing.tenancy();
        if (version == null || version != tenancy.version()) {
            throw ApiException.conflict(CHANGED);
        }
        validatePeriod(input);
        if (!tenancy.unitId().equals(input.unitId())) {
            requireRentalUnit(userId, input.unitId());
        }
        var tenants = tenancy.tenantsDeleted() ? null : tenants(input.tenants());
        if (store.overlaps(input.unitId(), input.startDate(), input.endDate(), id)) {
            throw ApiException.conflict(OVERLAP);
        }
        var terms = existing.terms();
        if (!input.startDate().equals(tenancy.startDate())) {
            // The first rent is valid from the start and follows it; later ones must stay after it.
            if (terms.stream().skip(1).anyMatch(t -> !t.validFrom().isAfter(input.startDate()))) {
                throw ApiException.badRequest(TERM_BEFORE_NEW_START);
            }
            store.moveTerm(terms.getFirst().id(), input.startDate());
        }
        try {
            if (!store.update(id, version, input.unitId(), input.startDate(), input.endDate(),
                    amount(input.depositAmount(), true), input.depositAmount() == null ? null : input.depositReceivedOn())) {
                throw ApiException.conflict(CHANGED);
            }
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(OVERLAP);
        }
        if (tenants != null) {
            store.replaceTenants(id, tenants);
        }
        if (!tenancy.unitId().equals(input.unitId())) {
            // The payments' transactions follow the tenancy to its new unit (design D4 of rent-payments).
            for (var payment : existing.payments()) {
                managed.retarget(userId, payment.transactionId(), input.unitId());
            }
        }
        return get(userId, id);
    }

    @Transactional
    void delete(Long userId, Long id) {
        requireEditable(userId, id);
        if (payments.exists(id)) {
            throw ApiException.conflict(HAS_PAYMENTS);
        }
        documents.deleteAll(TenancyDocumentOwner.TYPE, id);
        store.delete(id);
    }

    @Transactional
    Details deleteTenantData(Long userId, Long id) {
        var existing = requireEditable(userId, id);
        if (existing.state() != State.ENDED) {
            throw ApiException.conflict(NOT_ENDED);
        }
        store.deleteTenantData(id);
        return get(userId, id);
    }

    @Transactional
    Details addTerm(Long userId, Long id, RentInput rent) {
        var existing = requireEditable(userId, id);
        var items = items(rent);
        requireLaterTerm(existing, rent.validFrom(), null);
        store.insertTerm(id, rent.validFrom(), items);
        store.touch(id);
        return get(userId, id);
    }

    @Transactional
    Details updateTerm(Long userId, Long id, Long termId, RentInput rent) {
        var existing = requireEditable(userId, id);
        var term = term(existing, termId);
        var items = items(rent);
        var first = existing.terms().getFirst().id().equals(termId);
        // The first rent keeps the start of the tenancy as its date.
        var validFrom = first ? term.validFrom() : rent.validFrom();
        if (!first) {
            requireLaterTerm(existing, validFrom, termId);
        }
        store.updateTerm(termId, validFrom, items);
        store.touch(id);
        return get(userId, id);
    }

    @Transactional
    Details deleteTerm(Long userId, Long id, Long termId) {
        var existing = requireEditable(userId, id);
        term(existing, termId);
        if (existing.terms().getFirst().id().equals(termId)) {
            throw ApiException.conflict(FIRST_TERM);
        }
        store.deleteTerm(termId);
        store.touch(id);
        return get(userId, id);
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private List<Details> details(List<Tenancy> tenancies, Map<Long, UnitSummary> summaries, Map<Long, ShareRole> roles) {
        var ids = tenancies.stream().map(Tenancy::id).toList();
        var tenants = store.tenants(ids);
        var terms = store.rentTerms(ids);
        var today = GermanTime.today(clock);
        var paid = payments.ofTenancies(ids);
        return tenancies.stream().filter(t -> summaries.containsKey(t.unitId())).map(t -> {
            var own = terms.getOrDefault(t.id(), List.of());
            var state = t.startDate().isAfter(today) ? State.FUTURE
                    : t.endDate() != null && t.endDate().isBefore(today) ? State.ENDED : State.RUNNING;
            var at = state == State.FUTURE ? t.startDate() : state == State.ENDED ? t.endDate() : today;
            var current = own.stream().filter(term -> !term.validFrom().isAfter(at)).reduce((a, b) -> b)
                    .orElse(own.isEmpty() ? null : own.getFirst());
            var ownPayments = paid.getOrDefault(t.id(), List.of());
            var account = RentLedger.compute(own, t.startDate(), t.endDate(),
                    ownPayments.stream().map(RentPaymentStore.Payment::amount).toList(), today);
            return new Details(t, summaries.get(t.unitId()), roles.get(t.unitId()), tenants.getOrDefault(t.id(), List.of()),
                    own, current, state, ownPayments, account);
        }).toList();
    }

    /** The tenancy if the user may change it, e.g. to record payments; otherwise 404 or 403. */
    Details requireEditable(Long userId, Long id) {
        var details = get(userId, id);
        if (!details.canEdit()) {
            throw ApiException.forbidden(FORBIDDEN);
        }
        return details;
    }

    private void requireRentalUnit(Long userId, Long unitId) {
        var role = unitId == null ? null : units.roleOn(unitId, userId).orElse(null);
        if (role == null) {
            throw ApiException.notFound(UNIT_NOT_FOUND);
        }
        if (!role.atLeast(ShareRole.EDITOR)) {
            throw ApiException.forbidden(FORBIDDEN);
        }
        if (units.summaries(List.of(unitId)).get(unitId).usage() != UnitUsage.LONG_TERM_RENTAL) {
            throw ApiException.badRequest(WRONG_USAGE);
        }
    }

    private static void validatePeriod(TenancyInput input) {
        if (input.startDate() == null) {
            throw ApiException.badRequest("Bitte einen Beginn angeben.");
        }
        if (input.endDate() != null && input.endDate().isBefore(input.startDate())) {
            throw ApiException.badRequest(END_BEFORE_START);
        }
    }

    private static void requireLaterTerm(Details existing, LocalDate validFrom, Long exceptTermId) {
        if (validFrom == null) {
            throw ApiException.badRequest("Bitte angeben, ab wann die Miete gilt.");
        }
        if (existing.terms().stream().anyMatch(t -> !t.id().equals(exceptTermId) && t.validFrom().equals(validFrom))) {
            throw ApiException.conflict(DUPLICATE_TERM);
        }
        if (!validFrom.isAfter(existing.tenancy().startDate())) {
            throw ApiException.badRequest(TERM_BEFORE_START);
        }
    }

    private static RentTerm term(Details existing, Long termId) {
        return existing.terms().stream().filter(t -> t.id().equals(termId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Miete nicht gefunden"));
    }

    /** Cleaned tenants; every tenant needs a name, and there must be at least one. */
    private static List<Tenant> tenants(List<Tenant> input) {
        var result = new ArrayList<Tenant>();
        for (var t : input == null ? List.<Tenant>of() : input) {
            var tenant = new Tenant(null, text(t.name(), 200), text(t.street(), 200), text(t.postalCode(), 16),
                    text(t.city(), 128), text(t.country(), 64), text(t.phone(), 64), text(t.email(), 254));
            if (tenant.name() == null) {
                throw ApiException.badRequest(NO_TENANT);
            }
            result.add(tenant);
        }
        if (result.isEmpty()) {
            throw ApiException.badRequest(NO_TENANT);
        }
        return result;
    }

    private static List<RentItem> items(RentInput rent) {
        if (rent == null || rent.coldRent() == null) {
            throw ApiException.badRequest("Bitte eine Kaltmiete angeben.");
        }
        var items = new ArrayList<RentItem>();
        items.add(new RentItem(ItemKind.COLD_RENT, null, amount(rent.coldRent(), false)));
        if (rent.utilitiesAdvance() != null && rent.utilitiesAdvance().signum() != 0) {
            items.add(new RentItem(ItemKind.UTILITIES_ADVANCE, null, amount(rent.utilitiesAdvance(), false)));
        }
        for (var other : rent.others() == null ? List.<OtherItem>of() : rent.others()) {
            var label = text(other.label(), 100);
            if (label == null || other.amount() == null) {
                throw ApiException.badRequest("Bitte für jeden weiteren Posten Bezeichnung und Betrag angeben.");
            }
            items.add(new RentItem(ItemKind.OTHER, label, amount(other.amount(), false)));
        }
        return items;
    }

    private static BigDecimal amount(BigDecimal value, boolean optional) {
        if (value == null && optional) {
            return null;
        }
        if (value == null || value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0 || value.scale() > 2) {
            throw ApiException.badRequest("Bitte einen gültigen Betrag angeben.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var stripped = value.strip();
        if (stripped.length() > max) {
            throw ApiException.badRequest("Eine Angabe ist zu lang.");
        }
        return stripped;
    }
}
