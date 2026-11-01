package app.logis.core.users;

import app.logis.core.auth.RecoveryCodeService;
import app.logis.core.web.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * User management by admins (see spec user-management).
 */
@Service
@Transactional
class UserAdminService {

    static final String LAST_ADMIN = "Es muss mindestens ein aktiver Admin bestehen bleiben";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RecoveryCodeService recoveryCodes;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    UserAdminService(UserRepository users, PasswordEncoder encoder, RecoveryCodeService recoveryCodes, Clock clock,
            ApplicationEventPublisher events) {
        this.users = users;
        this.encoder = encoder;
        this.recoveryCodes = recoveryCodes;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    List<User> list() {
        return users.findAllByOrderByUsernameAsc();
    }

    User create(String username, String displayName, Role role, String password) {
        var name = username.trim();
        if (users.existsByUsernameIgnoreCase(name)) {
            throw ApiException.conflict("Benutzername ist bereits vergeben");
        }
        PasswordPolicy.requireValid(password);
        var display = displayName == null || displayName.isBlank() ? name : displayName.trim();
        var user = new User(name, display, role, encoder.encode(password), clock.instant());
        user.setPasswordChangeRequired(true);
        users.save(user);
        events.publishEvent(new UserCreatedEvent(user.getId(), user.getDisplayName()));
        return user;
    }

    User update(Long actingUserId, Long id, String displayName, Role role, Boolean enabled) {
        var user = load(id);
        if (enabled != null && !enabled && Objects.equals(actingUserId, id)) {
            throw ApiException.badRequest("Du kannst dein eigenes Konto nicht deaktivieren.");
        }
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isEnabled()
                && ((role != null && role != Role.ADMIN) || (enabled != null && !enabled));
        if (losesAdmin) {
            var activeAdmins = users.lockActiveAdmins();
            if (activeAdmins.size() <= 1) {
                throw ApiException.conflict(LAST_ADMIN);
            }
        }
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (role != null) {
            user.setRole(role);
        }
        if (enabled != null && enabled != user.isEnabled()) {
            user.setEnabled(enabled);
            if (!enabled) {
                user.invalidateSessions();
            }
        }
        return user;
    }

    User resetPassword(Long id, String password) {
        PasswordPolicy.requireValid(password);
        var user = load(id);
        user.setPasswordHash(encoder.encode(password));
        user.setPasswordChangeRequired(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.invalidateSessions();
        return user;
    }

    User resetTotp(Long id) {
        var user = load(id);
        user.setTotpSecretEnc(null);
        user.setTotpLastStep(null);
        recoveryCodes.deleteAll(user.getId());
        user.invalidateSessions();
        return user;
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Benutzer nicht gefunden"));
    }
}
