package app.logis.core.realestate;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Roles of a user on a property and its units, for data that hangs off either level (costs, finances).
 * The unit role is the effective role (the higher of property and unit share).
 */
@Component
class PropertyAccess {

    private final PropertyRepository properties;
    private final UnitRepository units;
    private final SharingService sharing;

    PropertyAccess(PropertyRepository properties, UnitRepository units, SharingService sharing) {
        this.properties = properties;
        this.units = units;
        this.sharing = sharing;
    }

    /** What a user may see of a property: their role on it (may be empty) and the visible units with roles. */
    record View(Property property, Optional<ShareRole> propertyRole, Map<Long, ShareRole> unitRoles) {

        /** Role on a target: the property itself ({@code unitId == null}) or one of its units. */
        Optional<ShareRole> roleOn(Long unitId) {
            return unitId == null ? propertyRole : Optional.ofNullable(unitRoles.get(unitId));
        }

        boolean canEdit(Long unitId) {
            return roleOn(unitId).filter(r -> r.atLeast(ShareRole.EDITOR)).isPresent();
        }
    }

    /** 404 if the user has no role on the property and none on any of its units. */
    View require(Long propertyId, Long userId) {
        var property = properties.findById(propertyId)
                .orElseThrow(() -> ApiException.notFound(PropertyService.NOT_FOUND));
        var propertyRole = sharing.roleOf(Property.SHARE_TYPE, propertyId, userId);
        var unitRoles = new LinkedHashMap<Long, ShareRole>();
        for (var unit : units.findByProperty(propertyId)) {
            sharing.roleOf(Unit.SHARE_TYPE, unit.getId(), userId).ifPresent(r -> unitRoles.put(unit.getId(), r));
        }
        if (propertyRole.isEmpty() && unitRoles.isEmpty()) {
            throw ApiException.notFound(PropertyService.NOT_FOUND);
        }
        return new View(property, propertyRole, unitRoles);
    }
}
