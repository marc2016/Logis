package app.logis.core.auth;

/**
 * The signed-in user in the SecurityContext.
 */
public record LogisPrincipal(Long userId, String username) {
}
