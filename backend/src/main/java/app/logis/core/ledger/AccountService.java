package app.logis.core.ledger;

import app.logis.core.sharing.ShareRole;
import app.logis.core.sharing.SharingService;
import app.logis.core.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accounts. Every access goes through the shares; without a share an account does not exist.
 */
@Service
@Transactional
class AccountService {

    private final AccountRepository accounts;
    private final LegalEntityService legalEntities;
    private final LegalEntityRepository legalEntityRepository;
    private final TransactionRepository transactions;
    private final SharingService sharing;
    private final Clock clock;

    AccountService(AccountRepository accounts, LegalEntityService legalEntities,
            LegalEntityRepository legalEntityRepository, TransactionRepository transactions, SharingService sharing,
            Clock clock) {
        this.accounts = accounts;
        this.legalEntities = legalEntities;
        this.legalEntityRepository = legalEntityRepository;
        this.transactions = transactions;
        this.sharing = sharing;
        this.clock = clock;
    }

    record AccountDetails(Account account, LegalEntity legalEntity, ShareRole myRole, BigDecimal balance) {
    }

    record AccountInput(String name, AccountKind kind, String iban, BigDecimal openingBalance) {
    }

    @Transactional(readOnly = true)
    List<AccountDetails> list(Long userId, boolean includeArchived) {
        var roles = sharing.rolesFor(Account.SHARE_TYPE, userId, ShareRole.VIEWER);
        var visible = accounts.findByIdIn(roles.keySet()).stream()
                .filter(a -> includeArchived || !a.isArchived())
                .toList();
        return details(visible, roles).stream()
                .sorted(Comparator.comparing((AccountDetails d) -> d.legalEntity().getName().toLowerCase())
                        .thenComparing(d -> d.account().getName().toLowerCase()))
                .toList();
    }

    @Transactional(readOnly = true)
    AccountDetails get(Long userId, Long accountId) {
        var role = sharing.requireRole(Account.SHARE_TYPE, accountId, userId, ShareRole.VIEWER);
        var account = accounts.findById(accountId).orElseThrow(() -> ApiException.notFound("Konto nicht gefunden"));
        return details(List.of(account), Map.of(accountId, role)).getFirst();
    }

    /** Checks the minimum role and returns the account. */
    @Transactional(readOnly = true)
    Account require(Long userId, Long accountId, ShareRole minimum) {
        sharing.requireRole(Account.SHARE_TYPE, accountId, userId, minimum);
        return accounts.findById(accountId).orElseThrow(() -> ApiException.notFound("Konto nicht gefunden"));
    }

    /** Like {@link #require}, and the account must not be archived (for entries). */
    @Transactional(readOnly = true)
    Account requireWritable(Long userId, Long accountId) {
        var account = require(userId, accountId, ShareRole.EDITOR);
        if (account.isArchived()) {
            throw ApiException.badRequest("Das Konto ist archiviert");
        }
        return account;
    }

    AccountDetails create(Long userId, Long legalEntityId, AccountInput input) {
        if (legalEntityId == null) {
            throw ApiException.badRequest("Bitte einen Rechtsträger wählen.");
        }
        var legalEntity = legalEntities.requireManaged(legalEntityId, userId);
        var account = accounts.save(new Account(legalEntity.getId(), requireName(input.name()), requireKind(input.kind()),
                Iban.normalize(input.iban()), openingBalance(input.openingBalance()), clock.instant()));
        sharing.grantOwner(Account.SHARE_TYPE, account.getId(), userId);
        return details(List.of(account), Map.of(account.getId(), ShareRole.OWNER)).getFirst();
    }

    AccountDetails update(Long userId, Long accountId, AccountInput input, boolean archived) {
        var account = require(userId, accountId, ShareRole.OWNER);
        account.setName(requireName(input.name()));
        account.setKind(requireKind(input.kind()));
        account.setIban(Iban.normalize(input.iban()));
        account.setOpeningBalance(openingBalance(input.openingBalance()));
        account.setArchived(archived);
        return details(List.of(account), Map.of(accountId, ShareRole.OWNER)).getFirst();
    }

    void delete(Long userId, Long accountId) {
        var account = require(userId, accountId, ShareRole.OWNER);
        if (transactions.countByAccountId(accountId) > 0) {
            throw ApiException.conflict("Das Konto hat noch Transaktionen und kann nur archiviert werden");
        }
        sharing.deleteAll(Account.SHARE_TYPE, accountId);
        accounts.delete(account);
    }

    private List<AccountDetails> details(List<Account> list, Map<Long, ShareRole> roles) {
        if (list.isEmpty()) {
            return List.of();
        }
        var ids = list.stream().map(Account::getId).toList();
        var sums = accounts.sumAmounts(ids).stream()
                .collect(Collectors.toMap(AccountRepository.BalanceRow::getAccountId, AccountRepository.BalanceRow::getTotal));
        var entities = legalEntityRepository.findAllById(list.stream().map(Account::getLegalEntityId).distinct().toList())
                .stream().collect(Collectors.toMap(LegalEntity::getId, Function.identity()));
        return list.stream()
                .map(a -> new AccountDetails(a, entities.get(a.getLegalEntityId()), roles.get(a.getId()),
                        a.getOpeningBalance().add(sums.getOrDefault(a.getId(), BigDecimal.ZERO))))
                .toList();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Bitte einen Namen angeben.");
        }
        if (name.trim().length() > 128) {
            throw ApiException.badRequest("Der Name darf höchstens 128 Zeichen lang sein.");
        }
        return name.trim();
    }

    private static AccountKind requireKind(AccountKind kind) {
        if (kind == null) {
            throw ApiException.badRequest("Bitte eine Kontoart wählen.");
        }
        return kind;
    }

    private static BigDecimal openingBalance(BigDecimal value) {
        return Amounts.requireMoney(value == null ? BigDecimal.ZERO : value, "Bitte einen Anfangssaldo angeben.");
    }
}
