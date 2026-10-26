package app.logis.core.users;

import app.logis.core.auth.LogisPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

    private final UserAdminService service;

    AdminUserController(UserAdminService service) {
        this.service = service;
    }

    /** Ausgelieferte Sicht - ohne Hashes, Secrets oder Codes. */
    record UserView(Long id, String username, String displayName, Role role, boolean enabled, boolean totpEnabled,
            Instant lastLoginAt, Instant createdAt) {

        static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                    u.isTotpEnabled(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    record CreateUserRequest(
            @NotBlank(message = "Bitte einen Benutzernamen angeben.")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,64}",
                    message = "Benutzername: 3-64 Zeichen, nur Buchstaben, Ziffern, Punkt, Binde- und Unterstrich.")
            String username,
            @Size(max = 128, message = "Der Anzeigename darf hoechstens 128 Zeichen lang sein.") String displayName,
            @NotNull(message = "Bitte eine Rolle waehlen.") Role role,
            @NotBlank(message = "Bitte ein Startpasswort angeben.") String password) {
    }

    record UpdateUserRequest(
            @Size(max = 128, message = "Der Anzeigename darf hoechstens 128 Zeichen lang sein.") String displayName,
            Role role,
            Boolean enabled) {
    }

    record ResetPasswordRequest(@NotBlank(message = "Bitte ein Startpasswort angeben.") String password) {
    }

    @GetMapping
    List<UserView> list() {
        return service.list().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserView create(@Valid @RequestBody CreateUserRequest body) {
        return UserView.of(service.create(body.username(), body.displayName(), body.role(), body.password()));
    }

    @PatchMapping("/{id}")
    UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body,
            @AuthenticationPrincipal LogisPrincipal principal) {
        return UserView.of(service.update(principal.userId(), id, body.displayName(), body.role(), body.enabled()));
    }

    @PostMapping("/{id}/reset-password")
    UserView resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest body) {
        return UserView.of(service.resetPassword(id, body.password()));
    }

    @PostMapping("/{id}/reset-totp")
    UserView resetTotp(@PathVariable Long id) {
        return UserView.of(service.resetTotp(id));
    }
}
