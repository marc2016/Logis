package app.logis.core.realestate;

import app.logis.core.ledger.Categories;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * The money view of a property: costs recorded on it plus items contributed by modules, per month of a year.
 * Transactions attributed to the property are not part of it.
 */
@Service
@Transactional(readOnly = true)
class PropertyFinanceService {

    static final String COST_SOURCE = "realestate.cost";

    private final PropertyAccess access;
    private final PropertyCostRepository costs;
    private final UnitRepository units;
    private final Categories categories;
    private final List<FinanceSource> sources;

    PropertyFinanceService(PropertyAccess access, PropertyCostRepository costs, UnitRepository units,
            Categories categories, List<FinanceSource> sources) {
        this.access = access;
        this.costs = costs;
        this.units = units;
        this.categories = categories;
        this.sources = sources;
    }

    enum SortKey {
        DATE, AMOUNT, UNIT, LABEL
    }

    record Filter(Long unitId, FinanceItem.Kind kind, SortKey sort, boolean ascending) {
    }

    record Item(LocalDate date, Long unitId, String unitName, FinanceItem.Kind kind, String label, BigDecimal amount,
            String sourceType, Long sourceId, String categoryName, String counterparty, boolean editable) {
    }

    record Month(int month, BigDecimal income, BigDecimal expenses, BigDecimal result) {
    }

    record Finances(int year, List<Month> months, Month total, List<Item> items, boolean canRecord) {
    }

    Finances get(Long userId, Long propertyId, int year, Filter filter) {
        var view = access.require(propertyId, userId);
        var from = LocalDate.of(year, 1, 1);
        var to = LocalDate.of(year, 12, 31);
        var unitNames = new java.util.HashMap<Long, String>();
        units.findByProperty(propertyId).forEach(u -> unitNames.put(u.getId(), u.getName()));

        var all = new ArrayList<Item>();
        var visibleCosts = costs.findInPeriod(propertyId, from, to).stream()
                .filter(c -> view.roleOn(c.getUnitId()).isPresent())
                .toList();
        var categoryNames = categories.names(visibleCosts.stream().map(PropertyCost::getCategoryId)
                .filter(Objects::nonNull).distinct().toList());
        for (var c : visibleCosts) {
            var category = c.getCategoryId() == null ? null : categoryNames.get(c.getCategoryId());
            var label = c.getCounterparty() != null ? c.getCounterparty() : category != null ? category : "Kosten";
            all.add(new Item(c.getDate(), c.getUnitId(), unitNames.get(c.getUnitId()), FinanceItem.Kind.EXPENSE,
                    label, c.getAmount(), COST_SOURCE, c.getId(), category, c.getCounterparty(),
                    view.canEdit(c.getUnitId())));
        }
        var visibleUnits = view.unitRoles().keySet();
        if (!visibleUnits.isEmpty()) {
            for (var source : sources) {
                for (var i : source.items(visibleUnits, from, to)) {
                    if (i.unitId() != null && visibleUnits.contains(i.unitId())) {
                        all.add(new Item(i.date(), i.unitId(), unitNames.get(i.unitId()), i.kind(), i.label(),
                                i.amount(), i.sourceType(), i.sourceId(), null, null, false));
                    }
                }
            }
        }

        if (filter.unitId() != null && !visibleUnits.contains(filter.unitId())) {
            throw ApiException.notFound("Einheit nicht gefunden");
        }
        var forTotals = all.stream()
                .filter(i -> filter.unitId() == null || filter.unitId().equals(i.unitId()))
                .toList();
        var months = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> month(m, forTotals.stream().filter(i -> i.date().getMonthValue() == m).toList()))
                .toList();
        var total = month(0, forTotals);
        var items = forTotals.stream()
                .filter(i -> filter.kind() == null || filter.kind() == i.kind())
                .sorted(comparator(filter))
                .toList();
        var canRecord = view.canEdit(null) || view.unitRoles().keySet().stream().anyMatch(view::canEdit);
        return new Finances(year, months, total, items, canRecord);
    }

    private static Month month(int month, List<Item> items) {
        var income = sum(items, FinanceItem.Kind.INCOME);
        var expenses = sum(items, FinanceItem.Kind.EXPENSE);
        return new Month(month, income, expenses, income.subtract(expenses));
    }

    private static BigDecimal sum(List<Item> items, FinanceItem.Kind kind) {
        return items.stream().filter(i -> i.kind() == kind).map(Item::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static Comparator<Item> comparator(Filter filter) {
        Comparator<Item> c = switch (filter.sort() == null ? SortKey.DATE : filter.sort()) {
            case DATE -> Comparator.comparing(Item::date);
            case AMOUNT -> Comparator.comparing(Item::amount);
            case UNIT -> Comparator.comparing(i -> i.unitName() == null ? "" : i.unitName().toLowerCase());
            case LABEL -> Comparator.comparing(i -> i.label() == null ? "" : i.label().toLowerCase());
        };
        if (!filter.ascending()) {
            c = c.reversed();
        }
        return c.thenComparing(Item::sourceType).thenComparing(Item::sourceId);
    }
}
