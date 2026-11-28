package app.logis.modules.longtermrental;

import app.logis.modules.longtermrental.TenancyStore.RentTerm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rent due and paid of a tenancy, month by month (spec rent-payments, „Rent due per month“ and „Allocation and
 * arrears“; design D3). Computed from the rent history and the payments, never stored.
 */
final class RentLedger {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    record Month(YearMonth month, BigDecimal due, BigDecimal paid, BigDecimal open, boolean overdue) {
    }

    /** {@code arrears} and {@code credit}: at most one of both is greater than zero. */
    record Account(List<Month> months, BigDecimal due, BigDecimal paid, BigDecimal arrears, BigDecimal credit,
            boolean overdue) {
    }

    private RentLedger() {
    }

    static Account compute(List<RentTerm> terms, LocalDate start, LocalDate end, List<BigDecimal> payments,
            LocalDate today) {
        var paidTotal = payments.stream().reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        var months = new ArrayList<Month>();
        var dueTotal = ZERO;
        var current = YearMonth.from(today);
        var last = end == null || YearMonth.from(end).isAfter(current) ? current : YearMonth.from(end);
        var sorted = terms.stream().sorted(Comparator.comparing(RentTerm::validFrom)).toList();
        var pool = paidTotal;
        if (!start.isAfter(today) && !sorted.isEmpty()) {
            for (var m = YearMonth.from(start); !m.isAfter(last); m = m.plusMonths(1)) {
                var from = m.atDay(1).isBefore(start) ? start : m.atDay(1);
                var to = end != null && end.isBefore(m.atEndOfMonth()) ? end : m.atEndOfMonth();
                var total = termAt(sorted, from).total();
                var days = to.toEpochDay() - from.toEpochDay() + 1;
                var due = days == m.lengthOfMonth() ? total.setScale(2, RoundingMode.HALF_UP)
                        : total.multiply(BigDecimal.valueOf(days))
                                .divide(BigDecimal.valueOf(m.lengthOfMonth()), 2, RoundingMode.HALF_UP);
                var paid = pool.min(due);
                pool = pool.subtract(paid);
                var open = due.subtract(paid);
                months.add(new Month(m, due, paid, open, open.signum() > 0 && today.isAfter(thirdWorkingDay(m))));
                dueTotal = dueTotal.add(due);
            }
        }
        var balance = paidTotal.subtract(dueTotal);
        return new Account(months, dueTotal, paidTotal, balance.signum() < 0 ? balance.negate() : ZERO,
                balance.signum() > 0 ? balance : ZERO, months.stream().anyMatch(Month::overdue));
    }

    /** The term valid on the day: the last one starting on or before it, else the first. */
    private static RentTerm termAt(List<RentTerm> sorted, LocalDate day) {
        RentTerm found = sorted.getFirst();
        for (var t : sorted) {
            if (!t.validFrom().isAfter(day)) {
                found = t;
            }
        }
        return found;
    }

    /** The third day from Monday to Friday of the month; holidays are not considered. */
    static LocalDate thirdWorkingDay(YearMonth month) {
        var day = month.atDay(1);
        int count = 0;
        while (true) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && ++count == 3) {
                return day;
            }
            day = day.plusDays(1);
        }
    }
}
