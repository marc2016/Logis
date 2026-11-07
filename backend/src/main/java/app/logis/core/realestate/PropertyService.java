package app.logis.core.realestate;

import app.logis.core.ledger.LegalEntities;
import app.logis.core.ledger.TransactionAttributions;
import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Properties and their units. A property is shared as a whole, units individually (only VIEWER/EDITOR).
 * Whoever only has a unit share sees the property as a frame with just their units.
 */
@Service
@Transactional
class PropertyService {

    static final String NOT_FOUND = "Objekt nicht gefunden";

    private final PropertyRepository properties;
    private final UnitRepository units;
    private final SharingService sharing;
    private final LegalEntities legalEntities;
    private final TransactionAttributions attributions;
    private final PropertyCostRepository costs;
    private final List<UnitReferences> unitReferences;
    private final Clock clock;

    PropertyService(PropertyRepository properties, UnitRepository units, SharingService sharing,
            LegalEntities legalEntities, TransactionAttributions attributions, PropertyCostRepository costs,
            List<UnitReferences> unitReferences, Clock clock) {
        this.properties = properties;
        this.units = units;
        this.sharing = sharing;
        this.legalEntities = legalEntities;
        this.attributions = attributions;
        this.costs = costs;
        this.unitReferences = unitReferences;
        this.clock = clock;
    }

    record UnitDetails(Unit unit, ShareRole myRole) {
    }

    /** {@code myRole} is the role on the property itself and {@code null} for unit-only access. */
    record PropertyDetails(Property property, LegalEntities.Summary legalEntity, ShareRole myRole,
            List<UnitDetails> units) {
    }

    record PropertyInput(String name, String street, String postalCode, String city) {
    }

    record UnitInput(String name, UnitUsage usage) {
    }

    // --- Reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<PropertyDetails> list(Long userId, boolean includeArchived) {
        var propertyRoles = sharing.rolesFor(Property.SHARE_TYPE, userId, ShareRole.VIEWER);
        var unitRoles = sharing.rolesFor(Unit.SHARE_TYPE, userId, ShareRole.VIEWER);
        var visibleUnits = units.findAllById(unitRoles.keySet());
        var propertyIds = new HashSet<>(propertyRoles.keySet());
        visibleUnits.forEach(u -> propertyIds.add(u.getPropertyId()));
        var list = properties.findByIdIn(propertyIds).stream()
                .filter(p -> includeArchived || !p.isArchived())
                .toList();
        return details(list, propertyRoles, visibleUnits, unitRoles).stream()
                .sorted(Comparator.comparing(d -> d.property().getName().toLowerCase()))
                .toList();
    }

    @Transactional(readOnly = true)
    PropertyDetails get(Long userId, Long propertyId) {
        var property = requireVisible(userId, propertyId);
        var role = sharing.roleOf(Property.SHARE_TYPE, propertyId, userId);
        var unitRoles = sharing.rolesFor(Unit.SHARE_TYPE, userId, ShareRole.VIEWER);
        var visibleUnits = units.findByProperty(propertyId).stream()
                .filter(u -> unitRoles.containsKey(u.getId()))
                .toList();
        return details(List.of(property),
                role.map(r -> Map.of(propertyId, r)).orElse(Map.of()), visibleUnits, unitRoles).getFirst();
    }

    // --- Properties ----------------------------------------------------------------------------

    PropertyDetails create(Long userId, Long legalEntityId, PropertyInput input, boolean multiUnit,
            UnitInput firstUnit) {
        if (legalEntityId == null) {
            throw ApiException.badRequest("Bitte einen Rechtsträger wählen.");
        }
        var legalEntity = legalEntities.requireManaged(legalEntityId, userId);
        var name = requireName(input.name());
        var usage = requireUsage(firstUnit == null ? null : firstUnit.usage());
        var unitName = multiUnit ? requireName(firstUnit.name(), "Bitte einen Namen für die erste Einheit angeben.") : name;
        var property = properties.save(new Property(legalEntity.id(), name, address(input), multiUnit, clock.instant()));
        units.save(new Unit(property.getId(), unitName, usage, 0, clock.instant()));
        sharing.grantOwner(Property.SHARE_TYPE, property.getId(), userId);
        return get(userId, property.getId());
    }

    PropertyDetails update(Long userId, Long propertyId, PropertyInput input) {
        var property = requireRole(userId, propertyId, ShareRole.OWNER);
        property.setName(requireName(input.name()));
        property.setAddress(address(input));
        return get(userId, propertyId);
    }

    PropertyDetails setArchived(Long userId, Long propertyId, boolean archived) {
        requireRole(userId, propertyId, ShareRole.OWNER).setArchived(archived);
        return get(userId, propertyId);
    }

    void delete(Long userId, Long propertyId) {
        var property = requireRole(userId, propertyId, ShareRole.OWNER);
        var unitIds = units.findIdsByProperty(propertyId);
        if (attributions.count(propertyId, unitIds) > 0) {
            throw ApiException.conflict("Dem Objekt sind noch Transaktionen zugeordnet und es kann nur archiviert werden");
        }
        if (costs.existsByPropertyId(propertyId)) {
            throw ApiException.conflict("Zum Objekt sind noch Kosten erfasst und es kann nur archiviert werden");
        }
        for (var references : unitReferences) {
            var reason = references.blockingReason(unitIds);
            if (reason != null) {
                throw ApiException.conflict(reason);
            }
        }
        unitIds.forEach(id -> sharing.deleteAll(Unit.SHARE_TYPE, id));
        sharing.deleteAll(Property.SHARE_TYPE, propertyId);
        units.deleteAllById(unitIds);
        properties.delete(property);
    }

    // --- Units ---------------------------------------------------------------------------------

    PropertyDetails addUnit(Long userId, Long propertyId, UnitInput input) {
        var property = requireRole(userId, propertyId, ShareRole.EDITOR);
        var name = requireName(input.name());
        var usage = requireUsage(input.usage());
        var existing = units.lockByProperty(propertyId);
        requireUniqueName(propertyId, name, null);
        var nextOrder = existing.stream().mapToInt(Unit::getSortOrder).max().orElse(-1) + 1;
        units.save(new Unit(propertyId, name, usage, nextOrder, clock.instant()));
        if (!existing.isEmpty()) {
            property.setMultiUnit(true);
        }
        return get(userId, propertyId);
    }

    PropertyDetails updateUnit(Long userId, Long unitId, UnitInput input) {
        var unit = requireUnit(userId, unitId);
        var name = requireName(input.name());
        requireUniqueName(unit.getPropertyId(), name, unitId);
        unit.setName(name);
        unit.setUsage(requireUsage(input.usage()));
        return get(userId, unit.getPropertyId());
    }

    PropertyDetails setUnitArchived(Long userId, Long unitId, boolean archived) {
        var unit = requireUnit(userId, unitId);
        if (archived && !unit.isArchived()) {
            var othersActive = units.lockByProperty(unit.getPropertyId()).stream()
                    .anyMatch(u -> !u.isArchived() && !u.getId().equals(unitId));
            if (!othersActive) {
                throw ApiException.conflict("Ein Objekt braucht mindestens eine aktive Einheit");
            }
        }
        unit.setArchived(archived);
        return get(userId, unit.getPropertyId());
    }

    // --- Helpers -------------------------------------------------------------------------------

    /** Units are managed by EDITORs of the property; a unit share alone does not suffice. */
    private Unit requireUnit(Long userId, Long unitId) {
        var unit = units.findById(unitId).orElseThrow(() -> ApiException.notFound("Einheit nicht gefunden"));
        sharing.requireRole(Unit.SHARE_TYPE, unitId, userId, ShareRole.VIEWER);
        requireRole(userId, unit.getPropertyId(), ShareRole.EDITOR);
        return unit;
    }

    /** Property-level access: 404 if the property is invisible, 403 for unit-only access or a role too low. */
    private Property requireRole(Long userId, Long propertyId, ShareRole minimum) {
        var property = requireVisible(userId, propertyId);
        var role = sharing.roleOf(Property.SHARE_TYPE, propertyId, userId);
        if (role.isEmpty() || !role.get().atLeast(minimum)) {
            throw ApiException.forbidden("Dafür fehlt die Berechtigung.");
        }
        return property;
    }

    private Property requireVisible(Long userId, Long propertyId) {
        var property = properties.findById(propertyId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        var visible = sharing.roleOf(Property.SHARE_TYPE, propertyId, userId).isPresent()
                || units.findIdsByProperty(propertyId).stream()
                        .anyMatch(id -> sharing.roleOf(Unit.SHARE_TYPE, id, userId).isPresent());
        if (!visible) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return property;
    }

    private List<PropertyDetails> details(List<Property> list, Map<Long, ShareRole> propertyRoles,
            Collection<Unit> visibleUnits, Map<Long, ShareRole> unitRoles) {
        if (list.isEmpty()) {
            return List.of();
        }
        var entities = legalEntities.summaries(list.stream().map(Property::getLegalEntityId).distinct().toList());
        return list.stream()
                .map(p -> new PropertyDetails(p, entities.get(p.getLegalEntityId()), propertyRoles.get(p.getId()),
                        visibleUnits.stream()
                                .filter(u -> u.getPropertyId().equals(p.getId()))
                                .sorted(Comparator.comparingInt(Unit::getSortOrder).thenComparing(Unit::getId))
                                .map(u -> new UnitDetails(u, unitRoles.get(u.getId())))
                                .toList()))
                .toList();
    }

    private void requireUniqueName(Long propertyId, String name, Long excludeId) {
        if (units.nameTaken(propertyId, name, excludeId)) {
            throw ApiException.conflict("Eine Einheit mit diesem Namen existiert bereits");
        }
    }

    private static Property.Address address(PropertyInput input) {
        return new Property.Address(optional(input.street(), 128), optional(input.postalCode(), 16),
                optional(input.city(), 128));
    }

    private static String optional(String value, int maxLength) {
        var clean = Optional.ofNullable(value).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
        if (clean != null && clean.length() > maxLength) {
            throw ApiException.badRequest("Die Adresse darf je Feld höchstens " + maxLength + " Zeichen lang sein.");
        }
        return clean;
    }

    private static String requireName(String name) {
        return requireName(name, "Bitte einen Namen angeben.");
    }

    private static String requireName(String name, String missingMessage) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest(missingMessage);
        }
        if (name.trim().length() > 128) {
            throw ApiException.badRequest("Der Name darf höchstens 128 Zeichen lang sein.");
        }
        return name.trim();
    }

    private static UnitUsage requireUsage(UnitUsage usage) {
        if (usage == null) {
            throw ApiException.badRequest("Bitte eine Nutzung wählen.");
        }
        return usage;
    }
}
