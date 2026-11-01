package app.logis.core.ledger;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/**
 * Filter of the transaction list. The set of accounts always comes from the user's shares.
 */
record TransactionFilter(Long accountId, LocalDate from, LocalDate to, Long categoryId, boolean uncategorized,
        String query) {

    Specification<Transaction> toSpecification(Collection<Long> accountIds, Collection<Long> categoryIds) {
        return (root, cq, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(root.get("accountId").in(accountIds));
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
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("counterparty")), pattern, '\\'),
                        cb.like(cb.lower(root.get("purpose")), pattern, '\\'),
                        cb.like(cb.lower(root.get("note")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
