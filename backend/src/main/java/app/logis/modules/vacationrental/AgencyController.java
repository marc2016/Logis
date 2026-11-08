package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The shared list of agencies; every signed-in user may change it (design.md D3). */
@RestController
@RequestMapping("/api/vacationrental/agencies")
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class AgencyController {

    private final AgencyService service;

    AgencyController(AgencyService service) {
        this.service = service;
    }

    record AliasView(Long id, String alias) {
    }

    /** {@code bookingCount} only counts bookings the user can see. */
    record AgencyView(Long id, String name, boolean archived, long version, List<AliasView> aliases,
            long bookingCount) {

        static AgencyView of(AgencyService.AgencyDetails d) {
            var a = d.agency();
            return new AgencyView(a.getId(), a.getName(), a.isArchived(), a.getVersion(),
                    d.aliases().stream().map(al -> new AliasView(al.getId(), al.getAlias())).toList(),
                    d.bookingCount());
        }
    }

    record NameRequest(String name) {
    }

    record RenameRequest(String name, Long version) {
    }

    record VersionRequest(Long version) {
    }

    record AliasRequest(String alias) {
    }

    record MergeRequest(Long targetId) {
    }

    @GetMapping
    List<AgencyView> list(@AuthenticationPrincipal LogisPrincipal me) {
        return service.list(me.userId()).stream().map(AgencyView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgencyView create(@RequestBody NameRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        return view(me, service.create(body.name()).getId());
    }

    @PutMapping("/{id}")
    AgencyView rename(@PathVariable Long id, @RequestBody RenameRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        service.rename(id, body.name(), body.version());
        return view(me, id);
    }

    @PostMapping("/{id}/archive")
    AgencyView archive(@PathVariable Long id, @RequestBody VersionRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        service.setArchived(id, true, body.version());
        return view(me, id);
    }

    @PostMapping("/{id}/restore")
    AgencyView restore(@PathVariable Long id, @RequestBody VersionRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        service.setArchived(id, false, body.version());
        return view(me, id);
    }

    @PostMapping("/{id}/aliases")
    AgencyView addAlias(@PathVariable Long id, @RequestBody AliasRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        service.addAlias(id, body.alias());
        return view(me, id);
    }

    @DeleteMapping("/{id}/aliases/{aliasId}")
    AgencyView removeAlias(@PathVariable Long id, @PathVariable Long aliasId,
            @AuthenticationPrincipal LogisPrincipal me) {
        service.removeAlias(id, aliasId);
        return view(me, id);
    }

    /** Merges this agency into {@code targetId}; returns the remaining agency. */
    @PostMapping("/{id}/merge")
    AgencyView merge(@PathVariable Long id, @RequestBody MergeRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return view(me, service.merge(id, body.targetId()).getId());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private AgencyView view(LogisPrincipal me, Long id) {
        return AgencyView.of(service.get(me.userId(), id));
    }
}
