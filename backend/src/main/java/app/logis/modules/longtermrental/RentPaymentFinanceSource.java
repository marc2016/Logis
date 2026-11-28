package app.logis.modules.longtermrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.FinanceItem;
import app.logis.core.realestate.FinanceSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rent payments as income of the property (spec property-finances, „Contributed items“; design D5 of rent-payments).
 * Their transactions are not part of the property finances, so nothing counts twice.
 */
@Component
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class RentPaymentFinanceSource implements FinanceSource {

    /** Links an item to its tenancy. */
    static final String SOURCE_TYPE = "longtermrental.tenancy";

    private final RentPaymentStore payments;
    private final TenancyStore tenancies;

    RentPaymentFinanceSource(RentPaymentStore payments, TenancyStore tenancies) {
        this.payments = payments;
        this.tenancies = tenancies;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinanceItem> items(Collection<Long> unitIds, LocalDate from, LocalDate to) {
        var list = payments.ofUnits(unitIds, from, to);
        if (list.isEmpty()) {
            return List.of();
        }
        var tenants = tenancies.tenants(list.stream().map(RentPaymentStore.UnitPayment::tenancyId).distinct().toList());
        return list.stream().map(p -> {
            var names = tenants.getOrDefault(p.tenancyId(), List.of()).stream().map(TenancyStore.Tenant::name)
                    .collect(Collectors.joining(", "));
            return new FinanceItem(p.date(), p.unitId(), FinanceItem.Kind.INCOME,
                    names.isEmpty() ? "Miete" : "Miete " + names, p.amount(), SOURCE_TYPE, p.tenancyId());
        }).toList();
    }
}
