package app.logis.core.auth;

import app.logis.core.users.Role;
import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * Endpunkte des mehrstufigen Logins (siehe design.md D6).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthFlowService flow;
    private final TotpService totp;
    private final UserRepository users;
    private final Clock clock;

    AuthController(AuthFlowService flow, TotpService totp, UserRepository users, Clock clock) {
        this.flow = flow;
        this.totp = totp;
        this.users = users;
        this.clock = clock;
    }

    record LoginRequest(String username, String password) {
    }

    record CodeRequest(String code) {
    }

    record PasswordRequest(String newPassword) {
    }

    record SessionUser(Long id, String username, String displayName, Role role) {
    }

    record SessionResponse(AuthStage stage, SessionUser user, List<String> recoveryCodes) {
    }

    record TotpSetupResponse(String secret, String otpauthUri) {
    }

    @GetMapping("/me")
    SessionResponse me(HttpServletRequest request) {
        var stage = AuthSession.stage(request);
        if (stage != AuthStage.AUTHENTICATED) {
            return new SessionResponse(stage, null, null);
        }
        var userId = (Long) request.getSession(false).getAttribute(AuthSession.USER_ID);
        var user = users.findById(userId).orElseThrow();
        return new SessionResponse(stage,
                new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()), null);
    }

    @PostMapping("/login")
    SessionResponse login(@RequestBody LoginRequest body, HttpServletRequest request) {
        var result = flow.checkPassword(body.username(), body.password(), request.getRemoteAddr());
        var user = result.user();
        AuthSession.start(request, user.getId(), user.getSessionVersion(), result.stage(), clock.instant());
        return new SessionResponse(result.stage(), null, null);
    }

    @PostMapping("/totp/setup")
    TotpSetupResponse setupTotp(HttpServletRequest request) {
        var userId = AuthSession.requireStage(request, AuthStage.TOTP_ENROLLMENT_REQUIRED);
        var session = request.getSession(false);
        var secret = (String) session.getAttribute(AuthSession.PENDING_TOTP_SECRET);
        if (secret == null) {
            secret = totp.generateSecret();
            session.setAttribute(AuthSession.PENDING_TOTP_SECRET, secret);
        }
        return new TotpSetupResponse(secret, totp.otpauthUri(flow.username(userId), secret));
    }

    @PostMapping("/totp/confirm")
    SessionResponse confirmTotp(@RequestBody CodeRequest body, HttpServletRequest request) {
        var userId = AuthSession.requireStage(request, AuthStage.TOTP_ENROLLMENT_REQUIRED);
        var secret = (String) request.getSession(false).getAttribute(AuthSession.PENDING_TOTP_SECRET);
        if (secret == null) {
            throw ApiException.badRequest("Bitte die Einrichtung zuerst starten.");
        }
        var result = flow.confirmEnrollment(userId, secret, body.code(), request.getRemoteAddr());
        AuthSession.advance(request, result.stage(), result.user().getSessionVersion());
        return new SessionResponse(result.stage(), null, result.recoveryCodes());
    }

    @PostMapping("/totp")
    SessionResponse verifyTotp(@RequestBody CodeRequest body, HttpServletRequest request) {
        var userId = AuthSession.requireStage(request, AuthStage.TOTP_REQUIRED);
        var result = flow.verifySecondFactor(userId, body.code(), request.getRemoteAddr());
        AuthSession.advance(request, result.stage(), result.user().getSessionVersion());
        return new SessionResponse(result.stage(), null, null);
    }

    @PostMapping("/password")
    SessionResponse changeRequiredPassword(@RequestBody PasswordRequest body, HttpServletRequest request) {
        var userId = AuthSession.requireStage(request, AuthStage.PASSWORD_CHANGE_REQUIRED);
        var result = flow.changeRequiredPassword(userId, body.newPassword());
        AuthSession.advance(request, result.stage(), result.user().getSessionVersion());
        return new SessionResponse(result.stage(), null, null);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        AuthSession.invalidate(request);
        return ResponseEntity.noContent().build();
    }
}
