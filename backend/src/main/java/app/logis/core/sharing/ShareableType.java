package app.logis.core.sharing;

/**
 * Registers a shareable object type, e.g. {@code account} or {@code vacationrental.property}.
 * Only registered types are reachable through the sharing API.
 */
public interface ShareableType {

    /** Stable type name, as stored in {@code core.object_shares.object_type}. */
    String type();
}
