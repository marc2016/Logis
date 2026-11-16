package app.logis.core.auth;

import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Registering, managing and signing in with passkeys (design.md D4, D5 of passkeys). */
@Service
@Transactional
public class PasskeyService {

    static final String NOT_CONFIGURED = "Passkeys sind nicht eingerichtet";
    static final String NOT_FOUND = "Passkey nicht gefunden";
    static final String WRONG_PASSWORD = "Das aktuelle Passwort ist falsch.";
    static final String REGISTRATION_FAILED = "Der Passkey konnte nicht hinzugefügt werden.";
    static final int MAX_NAME = 64;

    record PasskeyView(Long id, String name, Instant createdAt, Instant lastUsedAt) {

        static PasskeyView of(PasskeyStore.StoredPasskey p) {
            return new PasskeyView(p.id(), p.name(), p.createdAt(), p.lastUsedAt());
        }
    }

    private final ObjectProvider<RelyingParty> relyingParty;
    private final PasskeyStore store;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    PasskeyService(ObjectProvider<RelyingParty> relyingParty, PasskeyStore store, UserRepository users,
            PasswordEncoder encoder, Clock clock) {
        this.relyingParty = relyingParty;
        this.store = store;
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    boolean enabled() {
        return relyingParty.getIfAvailable() != null;
    }

    private RelyingParty rp() {
        var rp = relyingParty.getIfAvailable();
        if (rp == null) {
            throw ApiException.notFound(NOT_CONFIGURED);
        }
        return rp;
    }

    // --- Managing one's own passkeys -------------------------------------------------------------

    @Transactional(readOnly = true)
    List<PasskeyView> list(Long userId) {
        rp();
        return store.forUser(userId).stream().map(PasskeyView::of).toList();
    }

    /** Starts adding a passkey after the current password was confirmed; returns the options to keep. */
    PublicKeyCredentialCreationOptions startRegistration(Long userId, String password) {
        var rp = rp();
        var user = users.findById(userId).orElseThrow();
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest(WRONG_PASSWORD);
        }
        if (!user.isTotpEnabled()) {
            throw ApiException.badRequest("Bitte zuerst die Zwei-Faktor-Anmeldung einrichten.");
        }
        var handle = store.userHandleCreating(userId);
        return rp.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder().name(user.getUsername()).displayName(user.getDisplayName())
                        .id(new ByteArray(handle)).build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .build());
    }

    PasskeyView finishRegistration(Long userId, String requestJson, String responseJson, String name) {
        var rp = rp();
        var clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > MAX_NAME) {
            throw ApiException.badRequest("Bitte einen Namen mit höchstens 64 Zeichen angeben.");
        }
        if (requestJson == null) {
            throw ApiException.badRequest(REGISTRATION_FAILED);
        }
        try {
            var request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            var handle = store.userHandle(userId).orElseThrow();
            if (!Arrays.equals(request.getUser().getId().getBytes(), handle)) {
                throw ApiException.badRequest(REGISTRATION_FAILED);
            }
            var result = rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(request)
                    .response(PublicKeyCredential.parseRegistrationResponseJson(responseJson))
                    .build());
            var transports = result.getKeyId().getTransports()
                    .map(set -> set.stream().map(AuthenticatorTransport::getId).sorted().collect(Collectors.joining(",")))
                    .orElse(null);
            var id = store.insert(userId, result.getKeyId().getId().getBytes(), result.getPublicKeyCose().getBytes(),
                    result.getSignatureCount(), transports, clean, clock.instant());
            return store.forUser(userId).stream().filter(p -> p.id().equals(id)).map(PasskeyView::of).findFirst()
                    .orElseThrow();
        } catch (RegistrationFailedException | IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            if (e instanceof org.springframework.dao.DuplicateKeyException) {
                throw ApiException.conflict("Dieser Passkey ist bereits hinzugefügt.");
            }
            throw ApiException.badRequest(REGISTRATION_FAILED);
        }
    }

    PasskeyView rename(Long userId, Long id, String name) {
        rp();
        var clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > MAX_NAME) {
            throw ApiException.badRequest("Bitte einen Namen mit höchstens 64 Zeichen angeben.");
        }
        if (!store.rename(userId, id, clean)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return store.forUser(userId).stream().filter(p -> p.id().equals(id)).map(PasskeyView::of).findFirst()
                .orElseThrow();
    }

    void delete(Long userId, Long id) {
        rp();
        if (!store.delete(userId, id)) {
            throw ApiException.notFound(NOT_FOUND);
        }
    }

    /** All passkeys of a user, e.g. when an admin resets the two-factor authentication. */
    public void deleteAll(Long userId) {
        store.deleteAll(userId);
    }

    // --- Signing in ------------------------------------------------------------------------------

    /** Options for any passkey of this site (discoverable credentials), with user verification required. */
    AssertionRequest startAssertion() {
        return rp().startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build());
    }

    /** Outcome of a passkey sign-in attempt: the user, or why it failed (known user or not). */
    record Verified(Long userId, boolean success) {
    }

    /**
     * Verifies the passkey response against the request kept in the session; updates the counter and last use.
     * Returns the user of the passkey with the outcome, or {@code null} for an unknown passkey.
     */
    Verified finishAssertion(String requestJson, String responseJson) {
        var rp = rp();
        PublicKeyCredential<com.yubico.webauthn.data.AuthenticatorAssertionResponse,
                com.yubico.webauthn.data.ClientAssertionExtensionOutputs> response;
        AssertionRequest request;
        try {
            request = AssertionRequest.fromJson(requestJson);
            response = PublicKeyCredential.parseAssertionResponseJson(responseJson);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        var stored = store.byCredentialId(response.getId().getBytes()).orElse(null);
        if (stored == null) {
            return null;
        }
        try {
            var result = rp.finishAssertion(FinishAssertionOptions.builder().request(request).response(response).build());
            if (!result.isSuccess() || !result.isUserVerified()) {
                return new Verified(stored.userId(), false);
            }
            store.recordUse(stored.id(), result.getSignatureCount(), clock.instant());
            return new Verified(stored.userId(), true);
        } catch (AssertionFailedException | RuntimeException e) {
            return new Verified(stored.userId(), false);
        }
    }

    static ApiException failed() {
        return new ApiException(HttpStatus.UNAUTHORIZED, AuthFlowService.PASSKEY_FAILED);
    }
}
