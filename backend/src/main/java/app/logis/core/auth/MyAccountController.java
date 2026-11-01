package app.logis.core.auth;

import app.logis.core.users.PasswordPolicy;
import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "My account": change one's own password, regenerate recovery codes.
 */
@RestController
@RequestMapping("/api/account")
class MyAccountController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RecoveryCodeService recoveryCodes;

    MyAccountController(UserRepository users, PasswordEncoder encoder, RecoveryCodeService recoveryCodes) {
        this.users = users;
        this.encoder = encoder;
        this.recoveryCodes = recoveryCodes;
    }

    record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    record RecoveryCodesResponse(List<String> recoveryCodes) {
    }

    @PostMapping("/password")
    @Transactional
    void changePassword(@RequestBody ChangePasswordRequest body, @AuthenticationPrincipal LogisPrincipal principal,
            HttpServletRequest request) {
        var user = users.findById(principal.userId()).orElseThrow();
        if (body.currentPassword() == null || !encoder.matches(body.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Das aktuelle Passwort ist falsch.");
        }
        PasswordPolicy.requireValid(body.newPassword());
        user.setPasswordHash(encoder.encode(body.newPassword()));
        // End other sessions, keep the current one.
        user.invalidateSessions();
        AuthSession.updateVersion(request, user.getSessionVersion());
    }

    @PostMapping("/recovery-codes")
    RecoveryCodesResponse regenerateRecoveryCodes(@AuthenticationPrincipal LogisPrincipal principal) {
        return new RecoveryCodesResponse(recoveryCodes.regenerate(principal.userId()));
    }
}
