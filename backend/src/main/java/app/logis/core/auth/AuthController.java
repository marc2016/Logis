package app.logis.core.auth;

import app.logis.core.users.Role;
import app.logis.core.users.User;
import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Clock;
import java.util.List;

/**
 * Endpoints of the multi-step sign-in (see design.md D6).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthFlowService flow;
    private final TotpService totp;
    private final UserRepository users;
    private final Clock clock;

    private final PasskeyService passkeys;
    private final RememberedDeviceService devices;
    private final SignInMonitor monitor;

    AuthController(AuthFlowService flow, TotpService totp, UserRepository users, Clock clock,
            PasskeyService passkeys, RememberedDeviceService devices, SignInMonitor monitor) {
        this.passkeys = passkeys;
        this.devices = devices;
        this.monitor = monitor;
        this.flow = flow;
        this.totp = totp;
        this.users = users;
        this.clock = clock;
    }

    /** {@code rememberMe}: „Angemeldet bleiben“ (design D7 of remember-me). */
    record LoginRequest(String username, String password, Boolean rememberMe) {
    }

    record CodeRequest(String code) {
    }

    record PasswordRequest(String newPassword) {
    }

    /** {@code emailMissing}: the user has no e-mail address yet (design D5 of outgoing-mail). */
    record SessionUser(Long id, String username, String displayName, Role role, boolean emailMissing) {
    }

    /**
     * {@code passkeys}: whether passkey sign-in is offered; {@code reauthenticationRequired}: the session of an
     * admin was started by a remembered device, so the admin area needs a fresh sign-in (both only from {@code /me}).
     */
    record SessionResponse(AuthStage stage, SessionUser user, List<String> recoveryCodes, Boolean passkeys,
            Boolean reauthenticationRequired) {

        SessionResponse(AuthStage stage, SessionUser user, List<String> recoveryCodes) {
            this(stage, user, recoveryCodes, null, null);
        }
    }

    record TotpSetupResponse(String secret, String otpauthUri) {
    }

    @GetMapping("/me")
    SessionResponse me(HttpServletRequest request) {
        var stage = AuthSession.stage(request);
        if (stage != AuthStage.AUTHENTICATED) {
            return new SessionResponse(stage, null, null, passkeys.enabled(), null);
        }
        var session = request.getSession(false);
        var userId = (Long) session.getAttribute(AuthSession.USER_ID);
        var user = users.findById(userId).orElseThrow();
        var restored = Boolean.TRUE.equals(session.getAttribute(AuthSession.RESTORED));
        return new SessionResponse(stage,
                new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                        user.getEmail() == null), null,
                passkeys.enabled(), restored && user.getRole() == Role.ADMIN);
    }

    @PostMapping("/login")
    SessionResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var result = flow.checkPassword(body.username(), body.password(), request.getRemoteAddr());
        var user = result.user();
        startSignIn(request, user, result.stage(), body.rememberMe(), clock);
        completed(request, response, result, devices, monitor);
        return new SessionResponse(result.stage(), null, null);
    }

    @PostMapping("/totp/setup")
    TotpSetupResponse setupTotp(HttpServletRequest request) {
        var userId = AuthSession.requireStage(request, AuthStage.TOTP_ENROLLMENT_REQUIRED);
        var session = request.getSession(false);
        String secret;
        // Requests arriving at the same time (e.g. a page that asks twice) must get the same secret,
        // otherwise the one shown could differ from the one confirmed.
        synchronized (WebUtils.getSessionMutex(session)) {
            secret = (String) session.getAttribute(AuthSession.PENDING_TOTP_SECRET);
            if (secret == null) {
                secret = totp.generateSecret();
                session.setAttribute(AuthSession.PENDING_TOTP_SECRET, secret);
            }
        }
        return new TotpSetupResponse(secret, totp.otpauthUri(flow.username(userId), secret));
    }

    @PostMapping("/totp/confirm")
    SessionResponse confirmTotp(@RequestBody CodeRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        var userId = AuthSession.requireStage(request, AuthStage.TOTP_ENROLLMENT_REQUIRED);
        var secret = (String) request.getSession(false).getAttribute(AuthSession.PENDING_TOTP_SECRET);
        if (secret == null) {
            throw ApiException.badRequest("Bitte die Einrichtung zuerst starten.");
        }
        var result = flow.confirmEnrollment(userId, secret, body.code(), request.getRemoteAddr());
        AuthSession.advance(request, result.stage(), result.user().getSessionVersion());
        completed(request, response, result, devices, monitor);
        return new SessionResponse(result.stage(), null, result.recoveryCodes());
    }

    @PostMapping("/totp")
    SessionResponse verifyTotp(@RequestBody CodeRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        var userId = AuthSession.requireStage(request, AuthStage.TOTP_REQUIRED);
        var result = flow.verifySecondFactor(userId, body.code(), request.getRemoteAddr());
        AuthSession.advance(request, result.stage(), result.user().getSessionVersion());
        completed(request, response, result, devices, monitor);
        return new SessionResponse(result.stage(), null, null);
    }

    @PostMapping("/password")
    SessionResponse changeRequiredPassword(@RequestBody PasswordRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        var userId = AuthSession.requireStage(request, AuthStage.PASSWORD_CHANGE_REQUIRED);
        var result = flow.changeRequiredPassword(userId, body.newPassword());
        AuthSession.advance(request, result.stage(), result.user().getSessionVersion());
        completed(request, response, result, devices, monitor);
        return new SessionResponse(result.stage(), null, null);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        devices.forget(request, response);
        AuthSession.invalidate(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * „Erneut anmelden“ for the admin area: ends the session but keeps the device cookie; until the next sign-in
     * completes, the device does not start a session (design D9 of remember-me).
     */
    @PostMapping("/reauthenticate")
    ResponseEntity<Void> reauthenticate(HttpServletRequest request) {
        AuthSession.invalidate(request);
        request.getSession(true).setAttribute(AuthSession.SKIP_RESTORE, true);
        return ResponseEntity.noContent().build();
    }

    /** Starts the session of a new sign-in and keeps the „Angemeldet bleiben“ choice for its completion. */
    static void startSignIn(HttpServletRequest request, User user, AuthStage stage, Boolean rememberMe, Clock clock) {
        var session = AuthSession.start(request, user.getId(), user.getSessionVersion(), stage, clock.instant());
        if (Boolean.TRUE.equals(rememberMe)) {
            session.setAttribute(AuthSession.REMEMBER_REQUESTED, true);
        }
    }

    /**
     * Once a sign-in step completes the sign-in: remembers the device (or forgets a presented one) and checks the
     * sign-in for a new device or country (spec sign-in-alerts).
     */
    static void completed(HttpServletRequest request, HttpServletResponse response, AuthFlowService.StepResult result,
            RememberedDeviceService devices, SignInMonitor monitor) {
        if (result.stage() == AuthStage.AUTHENTICATED) {
            devices.onAuthenticated(request, response, result.user());
            monitor.signedIn(request, response, result.user());
        }
    }
}
