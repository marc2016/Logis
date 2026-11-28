package app.logis.modules.longtermrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitSummary;
import app.logis.modules.longtermrental.TenancyService.Details;
import app.logis.modules.longtermrental.TenancyService.OtherItem;
import app.logis.modules.longtermrental.TenancyService.RentInput;
import app.logis.modules.longtermrental.TenancyService.State;
import app.logis.modules.longtermrental.TenancyService.TenancyInput;
import app.logis.modules.longtermrental.TenancyStore.ItemKind;
import app.logis.modules.longtermrental.TenancyStore.RentTerm;
import app.logis.modules.longtermrental.TenancyStore.Tenant;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/longtermrental")
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class TenancyController {

    record UnitRef(Long id, String name, Long propertyId, String propertyName, boolean propertyMultiUnit) {

        static UnitRef of(UnitSummary u) {
            return new UnitRef(u.id(), u.name(), u.propertyId(), u.propertyName(), u.propertyMultiUnit());
        }
    }

    record OtherView(String label, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    record RentView(Long id, LocalDate validFrom, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal coldRent,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal utilitiesAdvance, List<OtherView> others,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {

        static RentView of(RentTerm term) {
            if (term == null) {
                return null;
            }
            return new RentView(term.id(), term.validFrom(), sum(term, ItemKind.COLD_RENT),
                    sum(term, ItemKind.UTILITIES_ADVANCE),
                    term.items().stream().filter(i -> i.kind() == ItemKind.OTHER)
                            .map(i -> new OtherView(i.label(), i.amount())).toList(),
                    term.total());
        }

        private static BigDecimal sum(RentTerm term, ItemKind kind) {
            return term.items().stream().filter(i -> i.kind() == kind).map(TenancyStore.RentItem::amount)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }
    }

    /** {@code tenants} carry contact data only for editors; they are empty once the tenant data is deleted. */
    record TenancyView(Long id, UnitRef unit, LocalDate startDate, LocalDate endDate, State state, List<Tenant> tenants,
            boolean tenantsDeleted, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal depositAmount,
            LocalDate depositReceivedOn, RentView currentRent, List<RentView> rentTerms, boolean canEdit, long version,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arrears,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal credit, boolean overdue) {

        static TenancyView of(Details d) {
            if (d == null) {
                return null;
            }
            var t = d.tenancy();
            var tenants = t.tenantsDeleted() ? List.<Tenant>of()
                    : d.canEdit() ? d.tenants()
                    : d.tenants().stream().map(x -> new Tenant(x.id(), x.name(), null, null, null, null, null, null)).toList();
            return new TenancyView(t.id(), UnitRef.of(d.unit()), t.startDate(), t.endDate(), d.state(), tenants,
                    t.tenantsDeleted(), t.depositAmount(), t.depositReceivedOn(), RentView.of(d.current()),
                    d.terms().stream().map(RentView::of).toList(), d.canEdit(), t.version(), d.account().arrears(),
                    d.account().credit(), d.account().overdue());
        }
    }

    /** A long-term unit: its running tenancy, or vacant since a day ({@code null}: never rented), and the next one. */
    record UnitView(UnitRef unit, boolean canEdit, TenancyView tenancy, LocalDate vacantSince, TenancyView next) {
    }

    record RentRequest(LocalDate validFrom, BigDecimal coldRent, BigDecimal utilitiesAdvance, List<OtherItem> others) {

        RentInput input() {
            return new RentInput(validFrom, coldRent, utilitiesAdvance, others);
        }
    }

    record TenancyRequest(Long unitId, LocalDate startDate, LocalDate endDate, BigDecimal depositAmount,
            LocalDate depositReceivedOn, List<Tenant> tenants, RentRequest rent, Long version) {

        TenancyInput input() {
            return new TenancyInput(unitId, startDate, endDate, depositAmount, depositReceivedOn, tenants);
        }
    }

    private final TenancyService service;

    TenancyController(TenancyService service) {
        this.service = service;
    }

    @GetMapping("/units")
    List<UnitView> units(@AuthenticationPrincipal LogisPrincipal me) {
        return service.units(me.userId()).stream()
                .map(u -> new UnitView(UnitRef.of(u.unit()), u.role().atLeast(app.logis.core.sharing.ShareRole.EDITOR),
                        TenancyView.of(u.current()), u.vacantSince(), TenancyView.of(u.next())))
                .toList();
    }

    @GetMapping("/tenancies")
    List<TenancyView> list(@RequestParam(required = false) Long propertyId, @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Set<State> state, @AuthenticationPrincipal LogisPrincipal me) {
        return service.list(me.userId(), propertyId, unitId, state).stream().map(TenancyView::of).toList();
    }

    @PostMapping("/tenancies")
    @ResponseStatus(HttpStatus.CREATED)
    TenancyView create(@RequestBody TenancyRequest request, @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.create(me.userId(), request.input(),
                request.rent() == null ? null : request.rent().input()));
    }

    @GetMapping("/tenancies/{id}")
    TenancyView get(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.get(me.userId(), id));
    }

    @PutMapping("/tenancies/{id}")
    TenancyView update(@PathVariable Long id, @RequestBody TenancyRequest request,
            @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.update(me.userId(), id, request.input(), request.version()));
    }

    @DeleteMapping("/tenancies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
    }

    @DeleteMapping("/tenancies/{id}/tenant-data")
    TenancyView deleteTenantData(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.deleteTenantData(me.userId(), id));
    }

    @PostMapping("/tenancies/{id}/rent-terms")
    @ResponseStatus(HttpStatus.CREATED)
    TenancyView addTerm(@PathVariable Long id, @RequestBody RentRequest request,
            @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.addTerm(me.userId(), id, request.input()));
    }

    @PutMapping("/tenancies/{id}/rent-terms/{termId}")
    TenancyView updateTerm(@PathVariable Long id, @PathVariable Long termId, @RequestBody RentRequest request,
            @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.updateTerm(me.userId(), id, termId, request.input()));
    }

    @DeleteMapping("/tenancies/{id}/rent-terms/{termId}")
    TenancyView deleteTerm(@PathVariable Long id, @PathVariable Long termId,
            @AuthenticationPrincipal LogisPrincipal me) {
        return TenancyView.of(service.deleteTerm(me.userId(), id, termId));
    }
}
