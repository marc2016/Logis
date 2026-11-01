package app.logis.core.ledger;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the transaction output with account, category and counter-account names in few queries.
 */
@Component
class TransactionViews {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryService categories;
    private final SharingService sharing;

    TransactionViews(AccountRepository accounts, TransactionRepository transactions, CategoryService categories,
            SharingService sharing) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.categories = categories;
        this.sharing = sharing;
    }

    record TransactionView(Long id, Long accountId, String accountName, LocalDate bookingDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, Long categoryId, String categoryName,
            String counterparty, String purpose, String note, UUID transferId, Long counterAccountId,
            String counterAccountName, int version) {
    }

    @Transactional(readOnly = true)
    List<TransactionView> of(List<Transaction> list, Long userId) {
        if (list.isEmpty()) {
            return List.of();
        }
        // Other sides of transfers
        var transferIds = list.stream().map(Transaction::getTransferId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var counterparts = transferIds.isEmpty() ? List.<Transaction>of() : transactions.findByTransferIdIn(transferIds);

        var accountIds = new HashSet<Long>();
        list.forEach(t -> accountIds.add(t.getAccountId()));
        counterparts.forEach(t -> accountIds.add(t.getAccountId()));
        var names = accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        // Show names of other people's counter-accounts only if the user may see them.
        var visible = sharing.accessibleIds(Account.SHARE_TYPE, userId, ShareRole.VIEWER);

        var categoryIds = list.stream().map(Transaction::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var categoryNames = categories.byIds(categoryIds).values().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        var byId = counterparts.stream().collect(Collectors.toMap(Transaction::getId, Function.identity()));

        return list.stream().map(t -> {
            Long counterId = null;
            String counterName = null;
            if (t.isTransfer()) {
                var other = byId.values().stream()
                        .filter(c -> c.getTransferId().equals(t.getTransferId()) && !c.getId().equals(t.getId()))
                        .findFirst().orElse(null);
                if (other != null) {
                    counterId = other.getAccountId();
                    counterName = visible.contains(counterId) ? names.get(counterId) : "anderes Konto";
                }
            }
            return new TransactionView(t.getId(), t.getAccountId(), names.get(t.getAccountId()), t.getBookingDate(),
                    t.getAmount(), t.getCategoryId(), categoryNames.get(t.getCategoryId()), t.getCounterparty(),
                    t.getPurpose(), t.getNote(), t.getTransferId(), counterId, counterName, t.getVersion());
        }).toList();
    }
}
