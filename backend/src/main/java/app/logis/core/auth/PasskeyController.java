package app.logis.core.auth;

import app.logis.core.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/** Passkey sign-in ({@code /api/auth/passkey}) and one's own passkeys ({@code /api/account/passkeys}). */
@RestController
class PasskeyController {

    static final String PENDING_ASSERTION = "logis.auth.pendingPasskeyAssertion";
    static final String PENDING_REGISTRATION = "logis.auth.pendingPasskeyRegistration";

    private final PasskeyService passkeys;
    private final AuthFlowService flow;
    private final ObjectMapper json;
    private final Clock clock;

    PasskeyController(PasskeyService passkeys, AuthFlowService flow, ObjectMapper json, Clock clock) {
        this.passkeys = passkeys;
        this.flow = flow;
        this.json = json;
        this.clock = clock;
    }

    record CredentialRequest(JsonNode credential) {
    }

    record RegistrationStart(String password) {
    }

    record RegistrationFinish(String name, JsonNode credential) {
    }

    record RenameRequest(String name) {
    }

    /** The `publicKey` part of the browser options, as the browser API expects it. */
    private JsonNode publicKey(String credentialsJson) throws JsonProcessingException {
        return json.readTree(credentialsJson).get("publicKey");
    }

    // --- Sign-in ---------------------------------------------------------------------------------

    @PostMapping("/api/auth/passkey/options")
    JsonNode signInOptions(HttpServletRequest request) throws JsonProcessingException {
        var assertion = passkeys.startAssertion();
        request.getSession(true).setAttribute(PENDING_ASSERTION, assertion.toJson());
        return publicKey(assertion.toCredentialsGetJson());
    }

    @PostMapping("/api/auth/passkey")
    AuthController.SessionResponse signIn(@RequestBody CredentialRequest body, HttpServletRequest request)
            throws JsonProcessingException {
        var session = request.getSession(false);
        var pending = session == null ? null : (String) session.getAttribute(PENDING_ASSERTION);
        // A challenge is used once, whatever the outcome.
        if (session != null) {
            session.removeAttribute(PENDING_ASSERTION);
        }
        var ip = request.getRemoteAddr();
        if (pending == null || body == null || body.credential() == null) {
            flow.unknownPasskey(ip);
            throw PasskeyService.failed();
        }
        var verified = passkeys.finishAssertion(pending, json.writeValueAsString(body.credential()));
        if (verified == null) {
            flow.unknownPasskey(ip);
            throw PasskeyService.failed();
        }
        if (!verified.success()) {
            flow.passkeyFailed(verified.userId(), ip);
            throw PasskeyService.failed();
        }
        var result = flow.signInWithPasskey(verified.userId(), ip);
        var user = result.user();
        AuthSession.start(request, user.getId(), user.getSessionVersion(), result.stage(), clock.instant());
        return new AuthController.SessionResponse(result.stage(), null, null);
    }

    // --- Own passkeys ----------------------------------------------------------------------------

    @GetMapping("/api/account/passkeys")
    List<PasskeyService.PasskeyView> list(@AuthenticationPrincipal LogisPrincipal me) {
        return passkeys.list(me.userId());
    }

    @PostMapping("/api/account/passkeys/registration/options")
    JsonNode registrationOptions(@RequestBody RegistrationStart body, @AuthenticationPrincipal LogisPrincipal me,
            HttpServletRequest request) throws JsonProcessingException {
        var options = passkeys.startRegistration(me.userId(), body == null ? null : body.password());
        request.getSession(false).setAttribute(PENDING_REGISTRATION, options.toJson());
        return publicKey(options.toCredentialsCreateJson());
    }

    @PostMapping("/api/account/passkeys/registration")
    PasskeyService.PasskeyView register(@RequestBody RegistrationFinish body, @AuthenticationPrincipal LogisPrincipal me,
            HttpServletRequest request) throws JsonProcessingException {
        var session = request.getSession(false);
        var pending = (String) session.getAttribute(PENDING_REGISTRATION);
        session.removeAttribute(PENDING_REGISTRATION);
        if (body == null || body.credential() == null) {
            throw ApiException.badRequest(PasskeyService.REGISTRATION_FAILED);
        }
        return passkeys.finishRegistration(me.userId(), pending, json.writeValueAsString(body.credential()), body.name());
    }

    @PutMapping("/api/account/passkeys/{id}")
    PasskeyService.PasskeyView rename(@PathVariable Long id, @RequestBody RenameRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return passkeys.rename(me.userId(), id, body == null ? null : body.name());
    }

    @DeleteMapping("/api/account/passkeys/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        passkeys.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
