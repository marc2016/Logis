package app.logis.core.ledger;

import app.logis.core.auth.LogisPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

@RestController
@RequestMapping("/api/legal-entities")
class LegalEntityController {

    private final LegalEntityService service;

    LegalEntityController(LegalEntityService service) {
        this.service = service;
    }

    record LegalEntityView(Long id, String name, LegalEntityKind kind, String taxNumber, boolean personal,
            long accountCount) {
    }

    record LegalEntityRequest(String name, @NotNull(message = "Bitte eine Art wählen.") LegalEntityKind kind,
            String taxNumber) {
    }

    @GetMapping
    List<LegalEntityView> list(@AuthenticationPrincipal LogisPrincipal me) {
        return service.listManaged(me.userId()).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LegalEntityView create(@Valid @RequestBody LegalEntityRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        return view(service.create(me.userId(), body.name(), body.kind(), body.taxNumber()));
    }

    @PutMapping("/{id}")
    LegalEntityView update(@PathVariable Long id, @Valid @RequestBody LegalEntityRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return view(service.update(me.userId(), id, body.name(), body.kind(), body.taxNumber()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private LegalEntityView view(LegalEntity e) {
        return new LegalEntityView(e.getId(), e.getName(), e.getKind(), e.getTaxNumber(), e.isPersonal(),
                service.accountCount(e.getId()));
    }
}
