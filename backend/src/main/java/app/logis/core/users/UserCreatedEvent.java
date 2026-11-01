package app.logis.core.users;

/**
 * Published synchronously in the transaction in which a user was created.
 */
public record UserCreatedEvent(Long userId, String displayName) {
}
