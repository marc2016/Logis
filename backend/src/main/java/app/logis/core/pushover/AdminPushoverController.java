package app.logis.core.pushover;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** „Pushover“ in the admin area (spec pushover). Never returns the token, only whether one is set. */
@RestController
@RequestMapping("/api/admin/pushover")
class AdminPushoverController {

    private final PushoverService service;
    private final PushoverStore store;

    AdminPushoverController(PushoverService service, PushoverStore store) {
        this.service = service;
        this.store = store;
    }

    record View(boolean configured, Instant updatedAt) {
    }

    record TokenRequest(String token) {
    }

    @GetMapping
    View get() {
        var updatedAt = store.tokenUpdatedAt();
        return new View(updatedAt.isPresent(), updatedAt.orElse(null));
    }

    @PutMapping
    View save(@RequestBody TokenRequest body) {
        service.saveToken(body.token());
        return get();
    }

    @DeleteMapping
    ResponseEntity<Void> delete() {
        service.deleteToken();
        return ResponseEntity.noContent().build();
    }
}
