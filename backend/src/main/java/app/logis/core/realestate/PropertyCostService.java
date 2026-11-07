package app.logis.core.realestate;

import app.logis.core.documents.Documents;
import app.logis.core.ledger.Categories;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Costs on properties and units. Recording needs EDITOR on the target; seeing a cost needs any role on it.
 */
@Service
@Transactional
class PropertyCostService {

    static final String NOT_FOUND = "Kosten nicht gefunden";
    static final String STALE = "Die Kosten wurden inzwischen geändert";
    private static final BigDecimal MAX = new BigDecimal("999999999.99");

    private final PropertyCostRepository costs;
    private final UnitRepository units;
    private final PropertyAccess access;
    private final Categories categories;
    private final Documents documents;
    private final Clock clock;

    PropertyCostService(PropertyCostRepository costs, UnitRepository units, PropertyAccess access,
            Categories categories, Documents documents, Clock clock) {
        this.costs = costs;
        this.units = units;
        this.access = access;
        this.categories = categories;
        this.documents = documents;
        this.clock = clock;
    }

    record CostInput(Long unitId, LocalDate date, BigDecimal amount, Long categoryId, String counterparty,
            String note) {
    }

    PropertyCost create(Long userId, Long propertyId, CostInput input) {
        var view = access.require(propertyId, userId);
        var cost = new PropertyCost(propertyId, clock.instant());
        apply(view, cost, input);
        return costs.save(cost);
    }

    @Transactional(readOnly = true)
    PropertyCost get(Long userId, Long costId) {
        return requireVisible(userId, costId);
    }

    PropertyCost update(Long userId, Long costId, CostInput input, Long version) {
        var cost = requireVisible(userId, costId);
        var view = access.require(cost.getPropertyId(), userId);
        requireEditor(view, cost.getUnitId());
        if (version == null || version != cost.getVersion()) {
            throw ApiException.conflict(STALE);
        }
        apply(view, cost, input);
        return cost;
    }

    void delete(Long userId, Long costId) {
        var cost = requireVisible(userId, costId);
        requireEditor(access.require(cost.getPropertyId(), userId), cost.getUnitId());
        documents.deleteAll(PropertyCost.DOCUMENT_TYPE, costId);
        costs.delete(cost);
    }

    /** Whether the user sees the cost (any role on its target). */
    @Transactional(readOnly = true)
    boolean canRead(Long costId, Long userId) {
        return costs.findById(costId).map(c -> visible(c, userId)).orElse(false);
    }

    /** Whether the user may change the cost (EDITOR on its target). */
    @Transactional(readOnly = true)
    boolean canWrite(Long costId, Long userId) {
        return costs.findById(costId)
                .map(c -> access.require(c.getPropertyId(), userId).canEdit(c.getUnitId()))
                .orElse(false);
    }

    private PropertyCost requireVisible(Long userId, Long costId) {
        var cost = costs.findById(costId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (!visible(cost, userId)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return cost;
    }

    private boolean visible(PropertyCost cost, Long userId) {
        try {
            return access.require(cost.getPropertyId(), userId).roleOn(cost.getUnitId()).isPresent();
        } catch (ApiException e) {
            return false;
        }
    }

    private void apply(PropertyAccess.View view, PropertyCost cost, CostInput input) {
        var unitId = input.unitId();
        if (unitId != null) {
            var unit = units.findById(unitId).filter(u -> u.getPropertyId().equals(view.property().getId()))
                    .orElseThrow(() -> ApiException.badRequest("Die Einheit gehört nicht zu diesem Objekt."));
            if (view.roleOn(unit.getId()).isEmpty()) {
                throw ApiException.notFound("Einheit nicht gefunden");
            }
        }
        requireEditor(view, unitId);
        if (input.date() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        var categoryId = input.categoryId() == null ? null : categories.requireSelectableExpense(input.categoryId());
        cost.apply(unitId, input.date(), requireAmount(input.amount()), categoryId,
                optional(input.counterparty(), 128, "Der Empfänger darf höchstens 128 Zeichen lang sein."),
                optional(input.note(), 2000, "Die Notiz darf höchstens 2000 Zeichen lang sein."));
    }

    private static void requireEditor(PropertyAccess.View view, Long unitId) {
        if (!view.canEdit(unitId)) {
            throw ApiException.forbidden("Dafür fehlt die Berechtigung.");
        }
    }

    static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("Bitte einen Betrag angeben.");
        }
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("Der Betrag muss größer als 0 sein.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("Der Betrag darf höchstens zwei Nachkommastellen haben.");
        }
        if (amount.compareTo(MAX) > 0) {
            throw ApiException.badRequest("Der Betrag ist zu groß.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String optional(String value, int maxLength, String tooLong) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var clean = value.trim();
        if (clean.length() > maxLength) {
            throw ApiException.badRequest(tooLong);
        }
        return clean;
    }
}
