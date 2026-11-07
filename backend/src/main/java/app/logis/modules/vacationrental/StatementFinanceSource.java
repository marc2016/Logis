package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.realestate.FinanceItem;
import app.logis.core.realestate.FinanceSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contributes settled money to the property finances: per statement line the gross as income and commission and
 * cleaning as expenses, all on the statement date.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class StatementFinanceSource implements FinanceSource {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.");

    private final StatementRepository statements;
    private final StatementLineRepository lines;
    private final BookingRepository bookings;

    StatementFinanceSource(StatementRepository statements, StatementLineRepository lines,
            BookingRepository bookings) {
        this.statements = statements;
        this.lines = lines;
        this.bookings = bookings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinanceItem> items(Collection<Long> unitIds, LocalDate from, LocalDate to) {
        var list = statements.findForUnitsInPeriod(unitIds, from, to);
        if (list.isEmpty()) {
            return List.of();
        }
        var byId = list.stream().collect(Collectors.toMap(Statement::getId, Function.identity()));
        var own = lines.findByStatementIdIn(byId.keySet());
        var bookingById = bookings.findAllById(own.stream().map(StatementLine::getBookingId).toList()).stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        var items = new ArrayList<FinanceItem>();
        for (var line : own) {
            var s = byId.get(line.getStatementId());
            var b = bookingById.get(line.getBookingId());
            items.add(item(s, FinanceItem.Kind.INCOME, label(b), line.getGross()));
            if (line.getCommission().signum() > 0) {
                items.add(item(s, FinanceItem.Kind.EXPENSE, "Provision", line.getCommission()));
            }
            if (line.getCleaning().signum() > 0) {
                items.add(item(s, FinanceItem.Kind.EXPENSE, "Reinigung", line.getCleaning()));
            }
        }
        return items;
    }

    private static FinanceItem item(Statement s, FinanceItem.Kind kind, String label, java.math.BigDecimal amount) {
        return new FinanceItem(s.getStatementDate(), s.getUnitId(), kind, label, amount, Statement.SOURCE_TYPE,
                s.getId());
    }

    private static String label(Booking b) {
        var prefix = b.isCancelled() ? "Stornierung " : "Buchung ";
        return b.getExternalRef() != null ? prefix + b.getExternalRef()
                : prefix + DAY.format(b.getArrival()) + "–" + DAY.format(b.getDeparture());
    }
}
