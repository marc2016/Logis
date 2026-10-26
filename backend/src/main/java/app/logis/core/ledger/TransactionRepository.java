package app.logis.core.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    long countByAccountId(Long accountId);

    long countByCategoryId(Long categoryId);

    List<Transaction> findByTransferId(UUID transferId);

    List<Transaction> findByTransferIdIn(Collection<UUID> transferIds);
}
