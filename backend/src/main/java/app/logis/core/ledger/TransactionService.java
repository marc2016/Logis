package app.logis.core.ledger;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import app.logis.core.web.ApiException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Transactions and transfers. Rights always come from the account.
 */
@Service
@Transactional
class TransactionService {

    static final String STALE = "Die Transaktion wurde inzwischen geändert";
    static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactions;
    private final AccountService accounts;
    private final CategoryService categories;
    private final SharingService sharing;
    private final EntityManager em;
    private final Clock clock;

    TransactionService(TransactionRepository transactions, AccountService accounts, CategoryService categories,
            SharingService sharing, EntityManager em, Clock clock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.sharing = sharing;
        this.em = em;
        this.clock = clock;
    }

    record TransactionInput(Long accountId, LocalDate bookingDate, BigDecimal amount, Long categoryId,
            String counterparty, String purpose, String note) {
    }

    record TransferInput(Long fromAccountId, Long toAccountId, LocalDate bookingDate, BigDecimal amount,
            String note) {
    }

    record ListResult(Page<Transaction> page, BigDecimal income, BigDecimal expense) {
    }

    // --- Single transactions -------------------------------------------------------------------

    Transaction create(Long userId, TransactionInput input) {
        accounts.requireWritable(userId, requireAccountId(input.accountId()));
        var t = new Transaction(input.accountId(), null, userId, clock.instant());
        apply(t, input, true);
        return transactions.save(t);
    }

    Transaction update(Long userId, Long id, TransactionInput input, Integer version) {
        var t = load(userId, id);
        if (t.isTransfer()) {
            throw ApiException.badRequest("Umbuchungen werden über die Umbuchung bearbeitet.");
        }
        accounts.requireWritable(userId, t.getAccountId());
        var targetAccount = requireAccountId(input.accountId());
        if (!targetAccount.equals(t.getAccountId())) {
            accounts.requireWritable(userId, targetAccount);
        }
        requireVersion(t, version);
        apply(t, input, !Objects.equals(input.categoryId(), t.getCategoryId()));
        t.setAccountId(targetAccount);
        t.touch(userId, clock.instant());
        return t;
    }

    /** Deletes a transaction; for a transfer, both sides. */
    void delete(Long userId, Long id) {
        var t = load(userId, id);
        if (t.isTransfer()) {
            deleteTransfer(userId, t.getTransferId());
            return;
        }
        accounts.requireWritable(userId, t.getAccountId());
        transactions.delete(t);
    }

    // --- Transfers -----------------------------------------------------------------------------

    List<Transaction> createTransfer(Long userId, TransferInput input) {
        var amount = requireTransferInput(userId, input);
        var transferId = UUID.randomUUID();
        var now = clock.instant();
        var out = new Transaction(input.fromAccountId(), transferId, userId, now);
        var in = new Transaction(input.toAccountId(), transferId, userId, now);
        fillTransfer(out, in, input, amount);
        return transactions.saveAll(List.of(out, in));
    }

    List<Transaction> updateTransfer(Long userId, UUID transferId, TransferInput input, Integer version) {
        var sides = loadTransfer(userId, transferId);
        var out = sides.get(0);
        var in = sides.get(1);
        accounts.requireWritable(userId, out.getAccountId());
        accounts.requireWritable(userId, in.getAccountId());
        var amount = requireTransferInput(userId, input);
        requireVersion(out, version);
        out.setAccountId(input.fromAccountId());
        in.setAccountId(input.toAccountId());
        fillTransfer(out, in, input, amount);
        out.touch(userId, clock.instant());
        in.touch(userId, clock.instant());
        return List.of(out, in);
    }

    void deleteTransfer(Long userId, UUID transferId) {
        var sides = loadTransfer(userId, transferId);
        for (var side : sides) {
            accounts.requireWritable(userId, side.getAccountId());
        }
        transactions.deleteAll(sides);
    }

    // --- List and totals -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    ListResult list(Long userId, TransactionFilter filter, int page, int size) {
        Set<Long> accountIds;
        if (filter.accountId() != null) {
            sharing.requireRole(Account.SHARE_TYPE, filter.accountId(), userId, ShareRole.VIEWER);
            accountIds = Set.of(filter.accountId());
        } else {
            accountIds = sharing.accessibleIds(Account.SHARE_TYPE, userId, ShareRole.VIEWER);
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("bookingDate"), Sort.Order.desc("id")));
        if (accountIds.isEmpty()) {
            return new ListResult(new PageImpl<>(List.of(), pageable, 0), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2));
        }
        var categoryIds = filter.categoryId() == null ? null : categories.withChildren(filter.categoryId());
        var spec = filter.toSpecification(accountIds, categoryIds);
        var result = transactions.findAll(spec, pageable);
        var sums = sums(spec);
        return new ListResult(result, sums[0], sums[1]);
    }

    /** Income and expenses across all hits, excluding transfers. */
    private BigDecimal[] sums(Specification<Transaction> spec) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(Object[].class);
        var root = query.from(Transaction.class);
        Specification<Transaction> withoutTransfers = spec.and((r, q, b) -> b.isNull(r.get("transferId")));
        query.where(withoutTransfers.toPredicate(root, query, cb));
        var amount = root.<BigDecimal>get("amount");
        var zero = cb.literal(BigDecimal.ZERO);
        var income = cb.sum(cb.<BigDecimal>selectCase().when(cb.gt(amount, BigDecimal.ZERO), amount).otherwise(zero));
        var expense = cb.sum(cb.<BigDecimal>selectCase().when(cb.lt(amount, BigDecimal.ZERO), amount).otherwise(zero));
        query.multiselect(cb.coalesce(income, zero), cb.coalesce(expense, zero));
        var row = em.createQuery(query).getSingleResult();
        return new BigDecimal[] {scale((BigDecimal) row[0]), scale((BigDecimal) row[1])};
    }

    // --- Helpers -------------------------------------------------------------------------------

    /** Loads a transaction if the user may see the account; otherwise 404. */
    private Transaction load(Long userId, Long id) {
        var t = transactions.findById(id).orElseThrow(() -> ApiException.notFound("Transaktion nicht gefunden"));
        if (sharing.roleOf(Account.SHARE_TYPE, t.getAccountId(), userId).isEmpty()) {
            throw ApiException.notFound("Transaktion nicht gefunden");
        }
        return t;
    }

    /** Both sides of a transfer, outgoing side first. */
    private List<Transaction> loadTransfer(Long userId, UUID transferId) {
        var sides = transactions.findByTransferId(transferId).stream()
                .sorted(Comparator.comparing(Transaction::getAmount))
                .toList();
        if (sides.size() != 2 || sides.stream()
                .allMatch(s -> sharing.roleOf(Account.SHARE_TYPE, s.getAccountId(), userId).isEmpty())) {
            throw ApiException.notFound("Umbuchung nicht gefunden");
        }
        return sides;
    }

    private BigDecimal requireTransferInput(Long userId, TransferInput input) {
        var from = requireAccountId(input.fromAccountId());
        var to = requireAccountId(input.toAccountId());
        if (from.equals(to)) {
            throw ApiException.badRequest("Quell- und Zielkonto müssen verschieden sein.");
        }
        accounts.requireWritable(userId, from);
        accounts.requireWritable(userId, to);
        if (input.bookingDate() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        var amount = Amounts.requireTransactionAmount(input.amount());
        if (amount.signum() < 0) {
            throw ApiException.badRequest("Der Betrag einer Umbuchung muss positiv sein.");
        }
        return amount;
    }

    private static void fillTransfer(Transaction out, Transaction in, TransferInput input, BigDecimal amount) {
        var note = limit(input.note(), Integer.MAX_VALUE, "Notiz");
        for (var side : List.of(out, in)) {
            side.setBookingDate(input.bookingDate());
            side.setCategoryId(null);
            side.setCounterparty(null);
            side.setPurpose(null);
            side.setNote(note);
        }
        out.setAmount(amount.negate());
        in.setAmount(amount);
    }

    private void apply(Transaction t, TransactionInput input, boolean categoryChanged) {
        if (input.bookingDate() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        t.setBookingDate(input.bookingDate());
        t.setAmount(Amounts.requireTransactionAmount(input.amount()));
        if (input.categoryId() == null) {
            t.setCategoryId(null);
        } else if (categoryChanged) {
            t.setCategoryId(categories.requireSelectable(input.categoryId()).getId());
        }
        t.setCounterparty(limit(input.counterparty(), 255, "Gegenpartei"));
        t.setPurpose(limit(input.purpose(), 500, "Verwendungszweck"));
        t.setNote(limit(input.note(), 10_000, "Notiz"));
    }

    private static void requireVersion(Transaction t, Integer version) {
        if (version == null || version != t.getVersion()) {
            throw ApiException.conflict(STALE);
        }
    }

    private static Long requireAccountId(Long accountId) {
        if (accountId == null) {
            throw ApiException.badRequest("Bitte ein Konto wählen.");
        }
        return accountId;
    }

    private static String limit(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var clean = value.trim();
        if (clean.length() > max) {
            throw ApiException.badRequest(field + " darf höchstens " + max + " Zeichen lang sein.");
        }
        return clean;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
