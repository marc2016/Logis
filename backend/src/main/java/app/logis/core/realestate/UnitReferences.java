package app.logis.core.realestate;

import java.util.Collection;

/**
 * Lets modules veto deleting a property while their data still refers to one of its units (like
 * {@code LegalEntityUsage} for legal entities).
 */
public interface UnitReferences {

    /** A message for users if one of the units is still referenced, otherwise {@code null}. */
    String blockingReason(Collection<Long> unitIds);
}
