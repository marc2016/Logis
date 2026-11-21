package app.logis.core.mail;

import java.util.Optional;
import java.util.Set;

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

    /**
     * Values to pseudonymize when mails of this owner are exported by an admin. Called once per exported mailbox, so
     * the result may hold values collected for the owner; it is then asked once per mail. Must not use AI services.
     */
    default SensitiveValues sensitiveValues(Long ownerUserId) {
        return mail -> Set.of();
    }

    /** Sender domains of agencies this handler knows; their addresses stay in the admin export. Lower case. */
    default Set<String> agencyDomains() {
        return Set.of();
    }

    /** German labels of the result codes this handler reports, as the inbox shows them; for the admin export. */
    default java.util.Map<String, String> resultLabels() {
        return java.util.Map.of();
    }

    /**
     * Result codes of this handler that leave something for the user to do, e.g. a mail waiting for a unit. Mails
     * with these results are what the inbox shows as „Offen“ and what users are notified about.
     */
    default Set<String> attentionResults() {
        return Set.of();
    }

    /** German labels of the kinds this handler reports, as the inbox shows them; for the admin export. */
    default java.util.Map<String, String> kindLabels() {
        return java.util.Map.of();
    }

    /** Sensitive values of one mail, see {@link #sensitiveValues(Long)}. */
    @FunctionalInterface
    interface SensitiveValues {

        Set<SensitiveValue> of(MailContent mail);
    }
}
