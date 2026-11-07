package app.logis.core.mail;

import java.util.Optional;

/**
 * Handles fetched mails it understands, e.g. booking mails in vacation rental. The core offers each mail to the
 * handlers in {@code @Order} until one returns a result. Handlers act with the rights of the mailbox owner.
 */
public interface MailHandler {

    /** Stable key, e.g. {@code vacationrental.booking}. */
    String key();

    /** Whether the handler sends mails to an AI service; the inbox settings say so. */
    default boolean usesAi() {
        return false;
    }

    /** What the handler did with the mail, or empty if the mail is not one it handles. */
    Optional<Handled> handle(Long ownerUserId, MailContent mail, byte[] raw);
}
