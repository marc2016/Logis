package app.logis.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

interface AccountRepository extends JpaRepository<Account, Long> {

    long countByLegalEntityId(Long legalEntityId);

    List<Account> findByIdIn(Collection<Long> ids);

    interface BalanceRow {
        Long getAccountId();

        BigDecimal getTotal();
    }

    /** Summe aller Transaktionsbetraege je Konto (inkl. Umbuchungen) in einer Abfrage. */
    @Query("select t.accountId as accountId, coalesce(sum(t.amount), 0) as total from Transaction t "
            + "where t.accountId in :ids group by t.accountId")
    List<BalanceRow> sumAmounts(Collection<Long> ids);
}
