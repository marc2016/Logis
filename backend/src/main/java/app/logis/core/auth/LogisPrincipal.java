package app.logis.core.auth;

/**
 * Der angemeldete Benutzer im SecurityContext.
 */
public record LogisPrincipal(Long userId, String username) {
}
