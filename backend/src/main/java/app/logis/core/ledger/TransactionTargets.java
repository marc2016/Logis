package app.logis.core.ledger;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Targets that transactions can be attributed to (properties and units). Declared here, implemented by the
 * real estate package, so that the ledger does not depend on it.
 */
public interface TransactionTargets {

    /**
     * Checks a new target: it must exist, be active, and the user needs at least EDITOR on it.
     * Exactly one of both IDs is set.
     */
    void requireAssignable(Long propertyId, Long unitId, Long userId);

    /** Properties on which the user has a role (unit-only access does not count). */
    Set<Long> visibleProperties(Long userId);

    /** Units on which the user has an effective role. */
    Set<Long> visibleUnits(Long userId);

    /** All units of a property. */
    Set<Long> unitsOf(Long propertyId);

    Map<Long, String> propertyNames(Collection<Long> propertyIds);

    /** Display names of units: "property · unit", or just the property name for single-unit properties. */
    Map<Long, String> unitNames(Collection<Long> unitIds);
}
