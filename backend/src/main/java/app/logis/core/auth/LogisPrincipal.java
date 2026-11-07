package app.logis.core.auth;

import org.springframework.modulith.NamedInterface;

/**
 * The signed-in user in the SecurityContext.
 */
@NamedInterface("auth")
public record LogisPrincipal(Long userId, String username) {
}
