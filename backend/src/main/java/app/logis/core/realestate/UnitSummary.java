package app.logis.core.realestate;

/**
 * Display data of a unit for modules.
 *
 * @param propertyMultiUnit whether the property shows its unit level (see {@code multiUnit} on properties)
 */
public record UnitSummary(Long id, String name, Long propertyId, String propertyName, boolean propertyMultiUnit,
        UnitUsage usage, boolean archived, boolean propertyArchived) {
}
