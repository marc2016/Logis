package app.logis.core.realestate;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.ledger.LegalEntityKind;
import app.logis.core.sharing.ShareRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.List;

@RestController
@RequestMapping("/api")
class PropertyController {

    private final PropertyService service;

    PropertyController(PropertyService service) {
        this.service = service;
    }

    record LegalEntityRef(Long id, String name, LegalEntityKind kind) {
    }

    record UnitView(Long id, String name, UnitUsage usage, boolean archived, ShareRole myRole) {
    }

    /** {@code myRole} is the role on the property itself, {@code null} for unit-only access. */
    record PropertyView(Long id, String name, String street, String postalCode, String city,
            LegalEntityRef legalEntity, boolean multiUnit, boolean archived, ShareRole myRole, List<UnitView> units) {

        static PropertyView of(PropertyService.PropertyDetails d) {
            var p = d.property();
            var a = p.getAddress();
            var e = d.legalEntity();
            return new PropertyView(p.getId(), p.getName(), a.street(), a.postalCode(), a.city(),
                    new LegalEntityRef(e.id(), e.name(), e.kind()), p.isMultiUnit(), p.isArchived(), d.myRole(),
                    d.units().stream()
                            .map(u -> new UnitView(u.unit().getId(), u.unit().getName(), u.unit().getUsage(),
                                    u.unit().isArchived(), u.myRole()))
                            .toList());
        }
    }

    record UnitRequest(String name, UnitUsage usage) {
    }

    record CreatePropertyRequest(Long legalEntityId, String name, String street, String postalCode, String city,
            boolean multiUnit, UnitRequest firstUnit) {
    }

    record UpdatePropertyRequest(String name, String street, String postalCode, String city) {
    }

    @GetMapping("/properties")
    List<PropertyView> list(@RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal LogisPrincipal me) {
        return service.list(me.userId(), includeArchived).stream().map(PropertyView::of).toList();
    }

    @GetMapping("/properties/{id}")
    PropertyView get(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.get(me.userId(), id));
    }

    @PostMapping("/properties")
    @ResponseStatus(HttpStatus.CREATED)
    PropertyView create(@RequestBody CreatePropertyRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        var input = new PropertyService.PropertyInput(body.name(), body.street(), body.postalCode(), body.city());
        var firstUnit = body.firstUnit() == null ? null
                : new PropertyService.UnitInput(body.firstUnit().name(), body.firstUnit().usage());
        return PropertyView.of(service.create(me.userId(), body.legalEntityId(), input, body.multiUnit(), firstUnit));
    }

    @PutMapping("/properties/{id}")
    PropertyView update(@PathVariable Long id, @RequestBody UpdatePropertyRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        var input = new PropertyService.PropertyInput(body.name(), body.street(), body.postalCode(), body.city());
        return PropertyView.of(service.update(me.userId(), id, input));
    }

    @PostMapping("/properties/{id}/archive")
    PropertyView archive(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.setArchived(me.userId(), id, true));
    }

    @PostMapping("/properties/{id}/restore")
    PropertyView restore(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.setArchived(me.userId(), id, false));
    }

    @DeleteMapping("/properties/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/properties/{id}/units")
    @ResponseStatus(HttpStatus.CREATED)
    PropertyView addUnit(@PathVariable Long id, @RequestBody UnitRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.addUnit(me.userId(), id,
                new PropertyService.UnitInput(body.name(), body.usage())));
    }

    @PutMapping("/units/{id}")
    PropertyView updateUnit(@PathVariable Long id, @RequestBody UnitRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.updateUnit(me.userId(), id,
                new PropertyService.UnitInput(body.name(), body.usage())));
    }

    @PostMapping("/units/{id}/archive")
    PropertyView archiveUnit(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.setUnitArchived(me.userId(), id, true));
    }

    @PostMapping("/units/{id}/restore")
    PropertyView restoreUnit(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return PropertyView.of(service.setUnitArchived(me.userId(), id, false));
    }
}
