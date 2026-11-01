package app.logis.core.auth;

import app.logis.core.users.PasswordPolicy;
import app.logis.core.users.User;
import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import app.logis.shared.SecretCipher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Logic of the individual sign-in steps. Failed attempts are stored even when sign-in is rejected
 * (hence {@code noRollbackFor}).
 */
@Service
@Transactional(noRollbackFor = ApiException.class)
class AuthFlowService {

    static final String INVALID_CREDENTIALS = "Benutzername oder Passwort ist falsch";
    static final String INVALID_CODE = "Code ist ungültig";
    static final String TOO_MANY_ATTEMPTS = "Zu viele Fehlversuche, bitte später erneut versuchen";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final LoginAttemptService attempts;
    private final TotpService totp;
    private final RecoveryCodeService recoveryCodes;
    private final SecretCipher cipher;
    private final Clock clock;
    private final String dummyHash;

    AuthFlowService(UserRepository users, PasswordEncoder encoder, LoginAttemptService attempts, TotpService totp,
            RecoveryCodeService recoveryCodes, SecretCipher cipher, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.attempts = attempts;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.clock = clock;
        this.dummyHash = encoder.encode("timing-equalizer-for-unknown-users");
    }

    record StepResult(User user, AuthStage stage, List<String> recoveryCodes) {
    }

    StepResult checkPassword(String username, String password, String ip) {
        var user = users.findByUsernameIgnoreCase(username == null ? "" : username.trim()).orElse(null);
        if (user == null) {
            encoder.matches(password == null ? "" : password, dummyHash);
            attempts.logFailure(username, ip, "PASSWORD");
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        requireNotLocked(user, ip, "PASSWORD");
        if (password == null || !encoder.matches(password, user.getPasswordHash()) || !user.isEnabled()) {
            attempts.recordFailure(user, ip, "PASSWORD");
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        // The counter is only reset after the second factor, otherwise the code step could be guessed without limit.
        var stage = user.isTotpEnabled() ? AuthStage.TOTP_REQUIRED : AuthStage.TOTP_ENROLLMENT_REQUIRED;
        return new StepResult(user, stage, List.of());
    }

    String username(Long userId) {
        return load(userId).getUsername();
    }

    StepResult confirmEnrollment(Long userId, String pendingSecret, String code, String ip) {
        var user = load(userId);
        requireNotLocked(user, ip, "TOTP_ENROLLMENT");
        var step = totp.verify(pendingSecret, code == null ? null : code.trim(), null);
        if (step.isEmpty()) {
            attempts.recordFailure(user, ip, "TOTP_ENROLLMENT");
            throw ApiException.unauthorized(INVALID_CODE);
        }
        user.setTotpSecretEnc(cipher.encrypt(pendingSecret));
        user.setTotpLastStep(step.getAsLong());
        var codes = recoveryCodes.regenerate(user.getId());
        return new StepResult(user, completeSecondFactor(user), codes);
    }

    StepResult verifySecondFactor(Long userId, String code, String ip) {
        var user = load(userId);
        requireNotLocked(user, ip, "TOTP");
        var input = code == null ? "" : code.trim();
        boolean ok;
        if (input.matches("\\d{6}")) {
            var step = totp.verify(cipher.decrypt(user.getTotpSecretEnc()), input, user.getTotpLastStep());
            step.ifPresent(user::setTotpLastStep);
            ok = step.isPresent();
        } else {
            ok = recoveryCodes.consume(user.getId(), input);
        }
        if (!ok) {
            attempts.recordFailure(user, ip, "TOTP");
            throw ApiException.unauthorized(INVALID_CODE);
        }
        return new StepResult(user, completeSecondFactor(user), List.of());
    }

    StepResult changeRequiredPassword(Long userId, String newPassword) {
        var user = load(userId);
        PasswordPolicy.requireValid(newPassword);
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("Das neue Passwort muss sich vom bisherigen unterscheiden.");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.invalidateSessions();
        user.setLastLoginAt(clock.instant());
        return new StepResult(user, AuthStage.AUTHENTICATED, List.of());
    }

    private AuthStage completeSecondFactor(User user) {
        attempts.recordSuccess(user);
        if (user.isPasswordChangeRequired()) {
            return AuthStage.PASSWORD_CHANGE_REQUIRED;
        }
        user.setLastLoginAt(clock.instant());
        return AuthStage.AUTHENTICATED;
    }

    private void requireNotLocked(User user, String ip, String stage) {
        if (attempts.isLocked(user)) {
            attempts.logFailure(user.getUsername(), ip, stage + "_LOCKED");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_ATTEMPTS);
        }
    }

    private User load(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Die Anmeldung ist abgelaufen. Bitte erneut anmelden."));
    }
}
