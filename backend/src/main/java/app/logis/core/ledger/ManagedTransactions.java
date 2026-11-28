package app.logis.core.ledger;

import app.logis.core.web.ApiException;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Transactions that a module creates and manages, e.g. the transaction of a rent payment (spec transactions,
 * „Transactions managed by a module“; design D1, D2 of rent-payments). The finances list them but do not change
 * them. The caller has checked its own rights (e.g. on the unit); the rights on the account are checked here.
 */
@Service
@Transactional
@NamedInterface("ledger")
public class ManagedTransactions {

    /** Category given to rent payments, if it exists and is active. */
    public static final String RENT_CATEGORY = "Mieteinnahmen";

    private final TransactionRepository transactions;
    private final AccountService accounts;
    private final AccountRepository accountRepository;
    private final CategoryRepository categories;
    private final Clock clock;

    ManagedTransactions(TransactionRepository transactions, AccountService accounts, AccountRepository accountRepository,
            CategoryRepository categories, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.categories = categories;
        this.clock = clock;
    }

    /** What the module sets; the target is a unit, the category is looked up by name among active income categories. */
    public record Input(Long accountId, LocalDate date, BigDecimal amount, String category, String counterparty,
            String purpose, Long unitId) {
    }

    /** Creates the transaction and returns its id. */
    public Long create(Long userId, String sourceType, Long sourceId, Input input) {
        accounts.requireWritable(userId, requireAccount(input.accountId()));
        var t = new Transaction(input.accountId(), null, userId, clock.instant());
        t.setSource(sourceType, sourceId);
        apply(t, input);
        return transactions.save(t).getId();
    }

    /** Changes the transaction; moving it to another account needs EDITOR on both. */
    public void update(Long userId, Long transactionId, Input input) {
        var t = require(transactionId);
        accounts.requireWritable(userId, t.getAccountId());
        var account = requireAccount(input.accountId());
        if (!account.equals(t.getAccountId())) {
            accounts.requireWritable(userId, account);
        }
        t.setAccountId(account);
        apply(t, input);
        t.touch(userId, clock.instant());
    }

    /** Moves the transaction to another unit, e.g. when a tenancy changes its unit; the account stays. */
    public void retarget(Long userId, Long transactionId, Long unitId) {
        var t = require(transactionId);
        t.setTarget(null, unitId);
        t.touch(userId, clock.instant());
    }

    public void delete(Long userId, Long transactionId) {
        var t = require(transactionId);
        accounts.requireWritable(userId, t.getAccountId());
        transactions.delete(t);
    }

    /** The account a managed transaction is booked on, for showing it to editors. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> accountNames(java.util.Collection<Long> transactionIds) {
        var result = new java.util.HashMap<Long, String>();
        var list = transactions.findAllById(transactionIds);
        var names = new java.util.HashMap<Long, String>();
        accountRepository.findAllById(list.stream().map(Transaction::getAccountId).distinct().toList())
                .forEach(a -> names.put(a.getId(), a.getName()));
        list.forEach(t -> result.put(t.getId(), names.get(t.getAccountId())));
        return result;
    }

    /** Account ids of managed transactions, e.g. to preselect the account of a payment being changed. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> accountIds(java.util.Collection<Long> transactionIds) {
        var result = new java.util.HashMap<Long, Long>();
        for (var t : transactions.findAllById(transactionIds)) {
            result.put(t.getId(), t.getAccountId());
        }
        return result;
    }

    private Transaction require(Long id) {
        return transactions.findById(id).filter(Transaction::isManaged)
                .orElseThrow(() -> ApiException.notFound("Transaktion nicht gefunden"));
    }

    private static Long requireAccount(Long accountId) {
        if (accountId == null) {
            throw ApiException.badRequest("Bitte ein Konto wählen.");
        }
        return accountId;
    }

    private void apply(Transaction t, Input input) {
        if (input.date() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        t.setBookingDate(input.date());
        t.setAmount(Amounts.requireTransactionAmount(input.amount()));
        t.setCategoryId(input.category() == null ? null : categories.findAllOrdered().stream()
                .filter(c -> c.getKind() == CategoryKind.INCOME && !c.isArchived()
                        && c.getName().equals(input.category()))
                .map(Category::getId).findFirst().orElse(null));
        t.setCounterparty(cut(input.counterparty(), 255));
        t.setPurpose(cut(input.purpose(), 500));
        t.setTarget(null, input.unitId());
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
