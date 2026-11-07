package app.logis.core.realestate;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * Public API for modules: effective roles on units (the higher of the property role and the unit role).
 * Modules never combine property and unit roles themselves.
 */
@Service
@Transactional(readOnly = true)
public class UnitAccess {

    private final SharingService sharing;
    private final UnitRepository units;
    private final PropertyRepository properties;

    UnitAccess(SharingService sharing, UnitRepository units, PropertyRepository properties) {
        this.sharing = sharing;
        this.units = units;
        this.properties = properties;
    }

    /** Effective role of the user on the unit; empty if the unit is invisible to them. */
    public Optional<ShareRole> roleOn(Long unitId, Long userId) {
        return sharing.roleOf(Unit.SHARE_TYPE, unitId, userId);
    }

    /** All units on which the user has at least the given effective role, with that role. */
    public Map<Long, ShareRole> units(Long userId, ShareRole minimum) {
        return sharing.rolesFor(Unit.SHARE_TYPE, userId, minimum);
    }

    /**
     * Display data of the given units. Performs no access check: callers pass only units they obtained from
     * {@link #units} or checked with {@link #roleOn}. Unknown IDs are left out.
     */
    public Map<Long, UnitSummary> summaries(Collection<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        var list = units.findAllById(unitIds);
        var propertyById = properties.findAllById(list.stream().map(Unit::getPropertyId).distinct().toList())
                .stream().collect(Collectors.toMap(Property::getId, Function.identity()));
        return list.stream().collect(Collectors.toMap(Unit::getId, u -> {
            var p = propertyById.get(u.getPropertyId());
            return new UnitSummary(u.getId(), u.getName(), p.getId(), p.getName(), p.isMultiUnit(), u.getUsage(),
                    u.isArchived(), p.isArchived());
        }));
    }
}
