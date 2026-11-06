package app.logis.core.realestate;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Public API for modules: effective roles on units (the higher of the property role and the unit role).
 * Modules never combine property and unit roles themselves.
 */
@Service
@Transactional(readOnly = true)
public class UnitAccess {

    private final SharingService sharing;

    UnitAccess(SharingService sharing) {
        this.sharing = sharing;
    }

    /** Effective role of the user on the unit; empty if the unit is invisible to them. */
    public Optional<ShareRole> roleOn(Long unitId, Long userId) {
        return sharing.roleOf(Unit.SHARE_TYPE, unitId, userId);
    }

    /** All units on which the user has at least the given effective role, with that role. */
    public Map<Long, ShareRole> units(Long userId, ShareRole minimum) {
        return sharing.rolesFor(Unit.SHARE_TYPE, userId, minimum);
    }
}
