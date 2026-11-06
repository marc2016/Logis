package app.logis.core.realestate;

import app.logis.core.ledger.TransactionTargets;
import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
class PropertyTransactionTargets implements TransactionTargets {

    private static final String ARCHIVED = "Archivierten Objekten oder Einheiten kann nichts zugeordnet werden.";

    private final PropertyRepository properties;
    private final UnitRepository units;
    private final SharingService sharing;

    PropertyTransactionTargets(PropertyRepository properties, UnitRepository units, SharingService sharing) {
        this.properties = properties;
        this.units = units;
        this.sharing = sharing;
    }

    @Override
    public void requireAssignable(Long propertyId, Long unitId, Long userId) {
        Property property;
        if (unitId != null) {
            var unit = units.findById(unitId).orElseThrow(() -> ApiException.notFound("Einheit nicht gefunden"));
            sharing.requireRole(Unit.SHARE_TYPE, unitId, userId, ShareRole.EDITOR);
            if (unit.isArchived()) {
                throw ApiException.badRequest(ARCHIVED);
            }
            property = properties.findById(unit.getPropertyId()).orElseThrow();
        } else {
            property = properties.findById(propertyId)
                    .orElseThrow(() -> ApiException.notFound(PropertyService.NOT_FOUND));
            sharing.requireRole(Property.SHARE_TYPE, propertyId, userId, ShareRole.EDITOR);
        }
        if (property.isArchived()) {
            throw ApiException.badRequest(ARCHIVED);
        }
    }

    @Override
    public Set<Long> visibleProperties(Long userId) {
        return sharing.accessibleIds(Property.SHARE_TYPE, userId, ShareRole.VIEWER);
    }

    @Override
    public Set<Long> visibleUnits(Long userId) {
        return sharing.accessibleIds(Unit.SHARE_TYPE, userId, ShareRole.VIEWER);
    }

    @Override
    public Set<Long> unitsOf(Long propertyId) {
        return new HashSet<>(units.findIdsByProperty(propertyId));
    }

    @Override
    public Map<Long, String> propertyNames(Collection<Long> propertyIds) {
        return properties.findAllById(propertyIds).stream()
                .collect(Collectors.toMap(Property::getId, Property::getName));
    }

    @Override
    public Map<Long, String> unitNames(Collection<Long> unitIds) {
        var list = units.findAllById(unitIds);
        var owners = properties.findAllById(list.stream().map(Unit::getPropertyId).distinct().toList()).stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));
        return list.stream().collect(Collectors.toMap(Unit::getId, u -> {
            var property = owners.get(u.getPropertyId());
            return property.isMultiUnit() ? property.getName() + " · " + u.getName() : property.getName();
        }));
    }
}
