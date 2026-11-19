package app.logis.core.users;

import app.logis.core.auth.LogisPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The own e-mail address under „Mein Konto“ (design D6 of outgoing-mail). */
@RestController
@RequestMapping("/api/account/email")
class AccountEmailController {

    private final EmailChangeService changes;

    AccountEmailController(EmailChangeService changes) {
        this.changes = changes;
    }

    record ChangeRequest(String email) {
    }

    record ConfirmRequest(String code) {
    }

    @GetMapping
    EmailChangeService.EmailView get(@AuthenticationPrincipal LogisPrincipal me) {
        return changes.view(me.userId());
    }

    @PostMapping
    ResponseEntity<Void> request(@RequestBody ChangeRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        changes.request(me.userId(), body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    EmailChangeService.EmailView confirm(@RequestBody ConfirmRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        changes.confirm(me.userId(), body.code());
        return changes.view(me.userId());
    }
}
