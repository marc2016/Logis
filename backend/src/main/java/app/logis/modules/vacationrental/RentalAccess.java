package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.UnitAccess;
import app.logis.core.realestate.UnitSummary;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Rights of the module come from the effective role on the unit; bookings and statements have no shares. */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class RentalAccess {

    static final String FORBIDDEN = "Dafür fehlt die Berechtigung.";

    private final UnitAccess units;

    RentalAccess(UnitAccess units) {
        this.units = units;
    }

    /** The effective role; 404 (with the given message) without one, 403 if below {@code minimum}. */
    ShareRole require(Long unitId, Long userId, ShareRole minimum, String notFound) {
        var role = unitId == null ? null : units.roleOn(unitId, userId).orElse(null);
        if (role == null) {
            throw ApiException.notFound(notFound);
        }
        if (!role.atLeast(minimum)) {
            throw ApiException.forbidden(FORBIDDEN);
        }
        return role;
    }

    /** The effective role, if any. */
    Optional<ShareRole> role(Long unitId, Long userId) {
        return units.roleOn(unitId, userId);
    }

    /** All units the user can see, with their effective role. */
    Map<Long, ShareRole> visibleUnits(Long userId) {
        return units.units(userId, ShareRole.VIEWER);
    }

    Map<Long, UnitSummary> summaries(Collection<Long> unitIds) {
        return units.summaries(unitIds);
    }

    static boolean canEdit(ShareRole role) {
        return role != null && role.atLeast(ShareRole.EDITOR);
    }
}
