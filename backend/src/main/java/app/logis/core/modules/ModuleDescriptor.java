package app.logis.core.modules;

/**
 * Describes an active domain module. Every module registers exactly one bean of this type
 * as long as it is enabled.
 *
 * @param id    stable module ID, identical to the ID in the frontend
 * @param label display name
 * @param order order in the navigation
 */
public record ModuleDescriptor(String id, String label, int order) {
}
