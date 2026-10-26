package app.logis.core.users;

/**
 * Wird synchron in der Transaktion veroeffentlicht, in der ein Benutzer angelegt wurde.
 */
public record UserCreatedEvent(Long userId, String displayName) {
}
