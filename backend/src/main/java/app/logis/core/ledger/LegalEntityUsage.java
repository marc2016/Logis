package app.logis.core.ledger;

/**
 * Lets other core packages veto deleting a legal entity they still refer to (e.g. properties).
 */
public interface LegalEntityUsage {

    /** A German message for users if the legal entity is still in use, otherwise {@code null}. */
    String blockingReason(Long legalEntityId);
}
