package app.logis.core.sharing;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Registers a shareable object type, e.g. {@code account} or {@code vacationrental.property}.
 * Only registered types are reachable through the sharing API.
 * <p>
 * A child type (e.g. {@code unit} below {@code property}) declares its parent type. The effective role on a
 * child object is the higher of the role on the child and the role on its parent; its shares are managed by
 * the parent's owners.
 */
public interface ShareableType {

    /** Stable type name, as stored in {@code core.object_shares.object_type}. */
    String type();

    /** Type of the parent object, or {@code null} for a top-level type. */
    default String parentType() {
        return null;
    }

    /** ID of the parent object of a child object; empty if the object does not exist. */
    default Optional<Long> parentOf(Long objectId) {
        return Optional.empty();
    }

    /** IDs of the child objects of a parent object. */
    default Set<Long> childrenOf(Long parentId) {
        return Set.of();
    }

    /** Roles that may be granted on objects of this type. */
    default Set<ShareRole> allowedRoles() {
        return EnumSet.allOf(ShareRole.class);
    }
}
