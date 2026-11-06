package app.logis.core.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Public read access for other core packages to transactions attributed to a property or its units.
 */
@Service
@Transactional(readOnly = true)
public class TransactionAttributions {

    private final TransactionRepository transactions;

    TransactionAttributions(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /** Number of transactions attributed to the property itself or to one of the given units. */
    public long count(Long propertyId, Collection<Long> unitIds) {
        // "in ()" is not valid JPQL, so an ID that never exists stands in for an empty list.
        return transactions.countAttributed(propertyId, unitIds.isEmpty() ? List.of(-1L) : unitIds);
    }
}
