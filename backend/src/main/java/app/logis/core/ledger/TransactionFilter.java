package app.logis.core.ledger;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Filter of the transaction list. The visible rows always come from the user's shares.
 */
record TransactionFilter(Long accountId, Long propertyId, Long unitId, LocalDate from, LocalDate to,
        Long categoryId, boolean uncategorized, String query) {

    /**
     * Rows a user may see: all rows of {@code accountIds} (full access), and rows attributed to
     * {@code unitIds} or {@code propertyIds} (read-only through the target).
     */
    record Visibility(Set<Long> accountIds, Set<Long> unitIds, Set<Long> propertyIds) {

        boolean isEmpty() {
            return accountIds.isEmpty() && unitIds.isEmpty() && propertyIds.isEmpty();
        }
    }

    /**
     * @param propertyUnits all units of the filtered property, or {@code null} without a property filter
     */
    Specification<Transaction> toSpecification(Visibility visibility, Collection<Long> categoryIds,
            Collection<Long> propertyUnits) {
        return (root, cq, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var visible = new ArrayList<Predicate>();
            if (!visibility.accountIds().isEmpty()) {
                visible.add(root.get("accountId").in(visibility.accountIds()));
            }
            if (!visibility.unitIds().isEmpty()) {
                visible.add(root.get("unitId").in(visibility.unitIds()));
            }
            if (!visibility.propertyIds().isEmpty()) {
                visible.add(root.get("propertyId").in(visibility.propertyIds()));
            }
            predicates.add(cb.or(visible.toArray(Predicate[]::new)));
            if (unitId != null) {
                predicates.add(cb.equal(root.get("unitId"), unitId));
            }
            if (propertyId != null) {
                var byProperty = cb.equal(root.get("propertyId"), propertyId);
                predicates.add(propertyUnits.isEmpty() ? byProperty
                        : cb.or(byProperty, root.get("unitId").in(propertyUnits)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bookingDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bookingDate"), to));
            }
            if (categoryIds != null) {
                predicates.add(root.get("categoryId").in(categoryIds));
            }
            if (uncategorized) {
                predicates.add(cb.isNull(root.get("categoryId")));
                predicates.add(cb.isNull(root.get("transferId")));
            }
            if (query != null && !query.isBlank()) {
                var pattern = "%" + query.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var text = new ArrayList<Predicate>();
                text.add(cb.like(cb.lower(root.get("counterparty")), pattern, '\\'));
                text.add(cb.like(cb.lower(root.get("purpose")), pattern, '\\'));
                // The note is only searched where the user sees the whole account.
                if (!visibility.accountIds().isEmpty()) {
                    text.add(cb.and(root.get("accountId").in(visibility.accountIds()),
                            cb.like(cb.lower(root.get("note")), pattern, '\\')));
                }
                predicates.add(cb.or(text.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
