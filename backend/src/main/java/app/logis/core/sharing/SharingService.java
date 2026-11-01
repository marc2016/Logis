package app.logis.core.sharing;

import app.logis.core.users.UserRepository;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shares per object. Without a share an object does not exist for the user (404);
 * with a role that is too low there is 403. The system role ADMIN plays no part here.
 */
@Service
@Transactional
public class SharingService {

    static final String NOT_FOUND = "Nicht gefunden";
    static final String LAST_OWNER = "Es muss mindestens einen Owner geben";

    private final ObjectShareRepository shares;
    private final UserRepository users;
    private final Clock clock;

    SharingService(ObjectShareRepository shares, UserRepository users, Clock clock) {
        this.shares = shares;
        this.users = users;
        this.clock = clock;
    }

    public record ShareView(Long userId, String username, String displayName, ShareRole role) {
    }

    public void grantOwner(String type, Long objectId, Long userId) {
        shares.save(new ObjectShare(type, objectId, userId, ShareRole.OWNER, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<ShareRole> roleOf(String type, Long objectId, Long userId) {
        return shares.find(type, objectId, userId).map(ObjectShare::getRole);
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

    /** All object IDs of a type on which the user has at least the role, with their role. */
    @Transactional(readOnly = true)
    public Map<Long, ShareRole> rolesFor(String type, Long userId, ShareRole minimum) {
        return shares.findAllForUser(type, userId).stream()
                .filter(s -> s.getRole().atLeast(minimum))
                .collect(Collectors.toMap(ObjectShare::getObjectId, ObjectShare::getRole));
    }

    @Transactional(readOnly = true)
    public Set<Long> accessibleIds(String type, Long userId, ShareRole minimum) {
        return rolesFor(type, userId, minimum).keySet();
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
        requireRole(type, objectId, actorId, ShareRole.OWNER);
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
        requireRole(type, objectId, actorId, ShareRole.OWNER);
        var share = shares.find(type, objectId, targetUserId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        changeRoleInternal(type, objectId, share, role);
    }

    /** Revokes a share. Owners may revoke any, every user their own. */
    public void revoke(String type, Long objectId, Long actorId, Long targetUserId) {
        requireRole(type, objectId, actorId,
                actorId.equals(targetUserId) ? ShareRole.VIEWER : ShareRole.OWNER);
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
