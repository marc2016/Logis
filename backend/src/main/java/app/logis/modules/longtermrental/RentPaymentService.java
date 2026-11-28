package app.logis.modules.longtermrental;

import app.logis.core.ledger.ManagedTransactions;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import app.logis.modules.longtermrental.TenancyService.Details;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rent payments with their transactions (spec rent-payments, „Record a payment“; design D1, D4). Payment and
 * transaction change together in one database transaction.
 */
@Service
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class RentPaymentService {

    static final String SOURCE_TYPE = "longtermrental.payment";
    static final String NOT_FOUND = "Zahlung nicht gefunden";
    static final String CHANGED = "Die Zahlung wurde inzwischen geändert";

    record PaymentInput(LocalDate date, BigDecimal amount, Long accountId, String note) {
    }

    /** The rent account of a tenancy; account names and ids only for editors. */
    record RentAccount(Details details, Map<Long, String> accountNames, Map<Long, Long> accountIds) {
    }

    private final TenancyService tenancies;
    private final RentPaymentStore payments;
    private final ManagedTransactions managed;

    RentPaymentService(TenancyService tenancies, RentPaymentStore payments, ManagedTransactions managed) {
        this.tenancies = tenancies;
        this.payments = payments;
        this.managed = managed;
    }

    @Transactional(readOnly = true)
    RentAccount account(Long userId, Long tenancyId) {
        var details = tenancies.get(userId, tenancyId);
        if (!details.canEdit()) {
            return new RentAccount(details, Map.of(), Map.of());
        }
        var transactionIds = details.payments().stream().map(RentPaymentStore.Payment::transactionId).toList();
        return new RentAccount(details, managed.accountNames(transactionIds), managed.accountIds(transactionIds));
    }

    @Transactional
    RentAccount record(Long userId, Long tenancyId, PaymentInput input) {
        var details = tenancies.requireEditable(userId, tenancyId);
        var amount = validate(input);
        var id = payments.insert(tenancyId, input.date(), amount, note(input.note()));
        var transactionId = managed.create(userId, SOURCE_TYPE, id, transaction(details, input, amount));
        payments.linkTransaction(id, transactionId);
        return account(userId, tenancyId);
    }

    @Transactional
    RentAccount update(Long userId, Long tenancyId, Long paymentId, PaymentInput input, Long version) {
        var details = tenancies.requireEditable(userId, tenancyId);
        var payment = require(details, paymentId);
        var amount = validate(input);
        if (version == null || !payments.update(paymentId, version, input.date(), amount, note(input.note()))) {
            throw ApiException.conflict(CHANGED);
        }
        managed.update(userId, payment.transactionId(), transaction(details, input, amount));
        return account(userId, tenancyId);
    }

    @Transactional
    RentAccount delete(Long userId, Long tenancyId, Long paymentId) {
        var details = tenancies.requireEditable(userId, tenancyId);
        var payment = require(details, paymentId);
        managed.delete(userId, payment.transactionId());
        payments.delete(paymentId);
        return account(userId, tenancyId);
    }

    private static RentPaymentStore.Payment require(Details details, Long paymentId) {
        return details.payments().stream().filter(p -> p.id().equals(paymentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private static BigDecimal validate(PaymentInput input) {
        if (input.date() == null) {
            throw ApiException.badRequest("Bitte ein Datum angeben.");
        }
        if (input.amount() == null || input.amount().signum() <= 0) {
            throw ApiException.badRequest("Bitte einen Betrag größer als 0 angeben.");
        }
        if (input.amount().scale() > 2 || input.amount().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw ApiException.badRequest("Bitte einen gültigen Betrag angeben, z. B. 800,00.");
        }
        if (input.accountId() == null) {
            throw ApiException.badRequest("Bitte ein Konto wählen.");
        }
        return input.amount().setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String note(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        var clean = note.trim();
        if (clean.length() > 500) {
            throw ApiException.badRequest("Die Notiz darf höchstens 500 Zeichen lang sein.");
        }
        return clean;
    }

    /** Category „Mieteinnahmen“, the unit as target, the tenants as counterparty (spec rent-payments). */
    private static ManagedTransactions.Input transaction(Details details, PaymentInput input, BigDecimal amount) {
        var tenancy = details.tenancy();
        var counterparty = tenancy.tenantsDeleted() ? null
                : details.tenants().stream().map(TenancyStore.Tenant::name).collect(Collectors.joining(", "));
        return new ManagedTransactions.Input(input.accountId(), input.date(), amount, ManagedTransactions.RENT_CATEGORY,
                counterparty, "Miete " + unitLabel(details), tenancy.unitId());
    }

    static String unitLabel(Details details) {
        var u = details.unit();
        return u.propertyMultiUnit() ? u.propertyName() + " · " + u.name() : u.propertyName();
    }

    static List<String> tenantNames(Details details) {
        return details.tenancy().tenantsDeleted() ? List.of()
                : details.tenants().stream().map(TenancyStore.Tenant::name).toList();
    }
}
