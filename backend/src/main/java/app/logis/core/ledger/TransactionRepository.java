package app.logis.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    long countByAccountId(Long accountId);

    long countByCategoryId(Long categoryId);

    @Query("select count(t) from Transaction t where t.propertyId = :propertyId or t.unitId in :unitIds")
    long countAttributed(Long propertyId, Collection<Long> unitIds);

    List<Transaction> findByTransferId(UUID transferId);

    List<Transaction> findByTransferIdIn(Collection<UUID> transferIds);
}
