package app.logis.core.ledger;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.sharing.ShareRole;
import com.fasterxml.jackson.annotation.JsonFormat;
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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
class AccountController {

    private final AccountService service;

    AccountController(AccountService service) {
        this.service = service;
    }

    /** Fremde Rechtstraeger erscheinen nur mit ID, Name und Art. */
    record LegalEntityRef(Long id, String name, LegalEntityKind kind) {
    }

    record AccountView(Long id, String name, AccountKind kind, String currency, String iban,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openingBalance,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
            boolean archived, ShareRole myRole, LegalEntityRef legalEntity) {

        static AccountView of(AccountService.AccountDetails d) {
            var a = d.account();
            var e = d.legalEntity();
            return new AccountView(a.getId(), a.getName(), a.getKind(), a.getCurrency(), a.getIban(),
                    a.getOpeningBalance(), d.balance(), a.isArchived(), d.myRole(),
                    new LegalEntityRef(e.getId(), e.getName(), e.getKind()));
        }
    }

    record CreateAccountRequest(Long legalEntityId, String name, AccountKind kind, String iban,
            BigDecimal openingBalance) {
    }

    record UpdateAccountRequest(String name, AccountKind kind, String iban, BigDecimal openingBalance,
            boolean archived) {
    }

    @GetMapping
    List<AccountView> list(@RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal LogisPrincipal me) {
        return service.list(me.userId(), includeArchived).stream().map(AccountView::of).toList();
    }

    @GetMapping("/{id}")
    AccountView get(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return AccountView.of(service.get(me.userId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountView create(@RequestBody CreateAccountRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        var input = new AccountService.AccountInput(body.name(), body.kind(), body.iban(), body.openingBalance());
        return AccountView.of(service.create(me.userId(), body.legalEntityId(), input));
    }

    @PutMapping("/{id}")
    AccountView update(@PathVariable Long id, @RequestBody UpdateAccountRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        var input = new AccountService.AccountInput(body.name(), body.kind(), body.iban(), body.openingBalance());
        return AccountView.of(service.update(me.userId(), id, input, body.archived()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
