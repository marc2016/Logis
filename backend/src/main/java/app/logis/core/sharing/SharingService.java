package app.logis.core.sharing;

import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shares per object. Without a share an object does not exist for the user (404);
 * with a role that is too low there is 403. The system role ADMIN plays no part here.
 * <p>
 * For child types the effective role is the higher of the child role and the parent role, and the
 * parent's owners manage the child's shares.
 */
@Service
@Transactional
public class SharingService {

    static final String NOT_FOUND = "Nicht gefunden";
    static final String LAST_OWNER = "Es muss mindestens einen Owner geben";
    static final String ROLE_NOT_ALLOWED = "Für Einheiten gibt es nur die Rollen Lesen und Bearbeiten";

    private final ObjectShareRepository shares;
    private final UserRepository users;
    private final Clock clock;
    private final Map<String, ShareableType> types;

    SharingService(ObjectShareRepository shares, UserRepository users, Clock clock, List<ShareableType> types) {
        this.shares = shares;
        this.users = users;
        this.clock = clock;
        this.types = types.stream().collect(Collectors.toMap(ShareableType::type, Function.identity()));
    }

    public record ShareView(Long userId, String username, String displayName, ShareRole role) {
    }

    public void grantOwner(String type, Long objectId, Long userId) {
        shares.save(new ObjectShare(type, objectId, userId, ShareRole.OWNER, clock.instant()));
    }

    /** Effective role of the user on the object; for child types including the parent role. */
    @Transactional(readOnly = true)
    public Optional<ShareRole> roleOf(String type, Long objectId, Long userId) {
        var direct = shares.find(type, objectId, userId).map(ObjectShare::getRole);
        var child = childType(type);
        if (child == null) {
            return direct;
        }
        var parentId = child.parentOf(objectId);
        if (parentId.isEmpty()) {
            return Optional.empty();
        }
        var inherited = shares.find(child.parentType(), parentId.get(), userId).map(ObjectShare::getRole);
        return Stream.of(direct, inherited).flatMap(Optional::stream).max(Enum::compareTo);
    }

    /**
     * Checks the minimum role and returns the actual role.
     *
     * @throws ApiException 404 without a share, 403 for a role that is too low
     */
    @Transactional(readOnly = true)
    public ShareRole requireRole(String type, Long objectId, Long userId, ShareRole required) {
        var role = roleOf(type, objectId, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (!role.atLeast(required)) {
            throw ApiException.forbidden("Dafür fehlt die Berechtigung.");
        }
        return role;
    }

    /** All object IDs of a type on which the user has at least the role, with their (effective) role. */
    @Transactional(readOnly = true)
    public Map<Long, ShareRole> rolesFor(String type, Long userId, ShareRole minimum) {
        var roles = new HashMap<Long, ShareRole>();
        for (var share : shares.findAllForUser(type, userId)) {
            roles.merge(share.getObjectId(), share.getRole(), SharingService::max);
        }
        var child = childType(type);
        if (child != null) {
            for (var parent : shares.findAllForUser(child.parentType(), userId)) {
                for (var childId : child.childrenOf(parent.getObjectId())) {
                    roles.merge(childId, parent.getRole(), SharingService::max);
                }
            }
        }
        roles.values().removeIf(role -> !role.atLeast(minimum));
        return roles;
    }

    @Transactional(readOnly = true)
    public Set<Long> accessibleIds(String type, Long userId, ShareRole minimum) {
        return rolesFor(type, userId, minimum).keySet();
    }

    /** Whether the user may manage the object's shares: owner of the object, or of the parent for child types. */
    @Transactional(readOnly = true)
    public boolean canManage(String type, Long objectId, Long userId) {
        var child = childType(type);
        if (child == null) {
            return roleOf(type, objectId, userId).filter(r -> r == ShareRole.OWNER).isPresent();
        }
        return child.parentOf(objectId)
                .flatMap(parentId -> shares.find(child.parentType(), parentId, userId))
                .filter(s -> s.getRole() == ShareRole.OWNER)
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<ShareView> list(String type, Long objectId, Long actorId) {
        requireRole(type, objectId, actorId, ShareRole.VIEWER);
        return shares.findAllForObject(type, objectId).stream()
                .map(s -> {
                    var user = users.findById(s.getUserId()).orElseThrow();
                    return new ShareView(user.getId(), user.getUsername(), user.getDisplayName(), s.getRole());
                })
                .toList();
    }

    /** Invites an existing, active user or changes their existing role. */
    public void invite(String type, Long objectId, Long actorId, Long targetUserId, ShareRole role) {
        requireManager(type, objectId, actorId);
        requireAllowed(type, role);
        var target = users.findById(targetUserId).filter(u -> u.isEnabled())
                .orElseThrow(() -> ApiException.notFound("Benutzer nicht gefunden"));
        var existing = shares.find(type, objectId, target.getId());
        if (existing.isPresent()) {
            changeRoleInternal(type, objectId, existing.get(), role);
        } else {
            shares.save(new ObjectShare(type, objectId, target.getId(), role, clock.instant()));
        }
    }

    public void changeRole(String type, Long objectId, Long actorId, Long targetUserId, ShareRole role) {
        requireManager(type, objectId, actorId);
        requireAllowed(type, role);
        var share = shares.find(type, objectId, targetUserId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        changeRoleInternal(type, objectId, share, role);
    }

    /** Revokes a share. Managers may revoke any, every user their own. */
    public void revoke(String type, Long objectId, Long actorId, Long targetUserId) {
        if (actorId.equals(targetUserId)) {
            requireRole(type, objectId, actorId, ShareRole.VIEWER);
        } else {
            requireManager(type, objectId, actorId);
        }
        var share = shares.find(type, objectId, targetUserId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (share.getRole() == ShareRole.OWNER) {
            requireAnotherOwner(type, objectId);
        }
        shares.delete(share);
    }

    /** When an object is deleted: remove all shares. */
    public void deleteAll(String type, Long objectId) {
        shares.deleteAllForObject(type, objectId);
    }

    private void requireManager(String type, Long objectId, Long actorId) {
        requireRole(type, objectId, actorId, ShareRole.VIEWER);
        if (!canManage(type, objectId, actorId)) {
            throw ApiException.forbidden("Dafür fehlt die Berechtigung.");
        }
    }

    private void requireAllowed(String type, ShareRole role) {
        var registered = types.get(type);
        if (registered != null && !registered.allowedRoles().contains(role)) {
            throw ApiException.badRequest(ROLE_NOT_ALLOWED);
        }
    }

    private ShareableType childType(String type) {
        var registered = types.get(type);
        return registered != null && registered.parentType() != null ? registered : null;
    }

    private static ShareRole max(ShareRole a, ShareRole b) {
        return a.atLeast(b) ? a : b;
    }

    private void changeRoleInternal(String type, Long objectId, ObjectShare share, ShareRole role) {
        if (share.getRole() == ShareRole.OWNER && role != ShareRole.OWNER) {
            requireAnotherOwner(type, objectId);
        }
        share.setRole(role);
    }

    private void requireAnotherOwner(String type, Long objectId) {
        if (shares.lockOwners(type, objectId).size() <= 1) {
            throw ApiException.conflict(LAST_OWNER);
        }
    }
}
