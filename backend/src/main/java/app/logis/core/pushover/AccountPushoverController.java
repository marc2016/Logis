package app.logis.core.pushover;

import app.logis.core.auth.LogisPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** The signed-in user's own Pushover key under „Mein Konto“ (spec pushover). Never returns the key. */
@RestController
@RequestMapping("/api/account/pushover")
class AccountPushoverController {

    private final PushoverService service;
    private final PushoverStore store;

    AccountPushoverController(PushoverService service, PushoverStore store) {
        this.service = service;
        this.store = store;
    }

    /**
     * @param available an admin stored an application token
     * @param invalid   Pushover rejected the stored key; nothing is sent to it
     */
    record View(boolean available, boolean hasKey, boolean invalid, Instant updatedAt) {
    }

    record KeyRequest(String userKey) {
    }

    @GetMapping
    View get(@AuthenticationPrincipal LogisPrincipal me) {
        var key = store.userKey(me.userId());
        return new View(store.token().isPresent(), key.isPresent(),
                key.map(PushoverStore.UserKey::invalid).orElse(false),
                key.map(PushoverStore.UserKey::updatedAt).orElse(null));
    }

    @PutMapping
    View save(@RequestBody KeyRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        service.saveUserKey(me.userId(), body.userKey());
        return get(me);
    }

    @DeleteMapping
    ResponseEntity<Void> delete(@AuthenticationPrincipal LogisPrincipal me) {
        service.deleteUserKey(me.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    ResponseEntity<Void> test(@AuthenticationPrincipal LogisPrincipal me) {
        service.sendTest(me.userId());
        return ResponseEntity.noContent().build();
    }
}
