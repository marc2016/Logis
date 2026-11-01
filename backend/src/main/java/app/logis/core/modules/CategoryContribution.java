package app.logis.core.modules;

/**
 * A category that a domain module brings along (e.g. "Reinigung" for vacation rental).
 * It is created at startup if missing; admins can only archive it.
 *
 * @param moduleId  ID of the module (as in the {@link ModuleDescriptor})
 * @param key       stable, globally unique key, e.g. {@code vacationrental.cleaning}
 * @param income    {@code true} for income, {@code false} for expenses
 * @param name      display name
 * @param parentKey key of this module's main category, or {@code null} for a main category
 */
public record CategoryContribution(String moduleId, String key, boolean income, String name, String parentKey) {
}
