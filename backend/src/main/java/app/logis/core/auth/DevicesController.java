package app.logis.core.auth;

import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** „Angemeldete Geräte“ under „Mein Konto“ (design.md of remember-me, D10). */
@RestController
@RequestMapping("/api/account/devices")
class DevicesController {

    private final RememberedDeviceService devices;
    private final UserRepository users;

    DevicesController(RememberedDeviceService devices, UserRepository users) {
        this.devices = devices;
        this.users = users;
    }

    @GetMapping
    List<RememberedDeviceService.DeviceView> list(@AuthenticationPrincipal LogisPrincipal me,
            HttpServletRequest request) {
        return devices.list(users.findById(me.userId()).orElseThrow(), request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> signOut(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        if (!devices.signOut(me.userId(), id)) {
            throw ApiException.notFound("Gerät nicht gefunden");
        }
        return ResponseEntity.noContent().build();
    }

    /** Ends all other sessions and remembered devices; the current device stays signed in (design D6). */
    @PostMapping("/sign-out-others")
    @Transactional
    ResponseEntity<Void> signOutOthers(@AuthenticationPrincipal LogisPrincipal me, HttpServletRequest request) {
        var user = users.findById(me.userId()).orElseThrow();
        user.invalidateSessions();
        AuthSession.updateVersion(request, user.getSessionVersion());
        devices.keepOnlyCurrent(request, user);
        return ResponseEntity.noContent().build();
    }
}
