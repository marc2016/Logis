package app.logis.core.realestate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An income or expense item of a unit, contributed by a module to the property finances.
 *
 * @param amount     always positive; {@code kind} decides the direction
 * @param sourceType stable key of the source, e.g. {@code vacationrental.statement}, for links in the interface
 */
public record FinanceItem(LocalDate date, Long unitId, Kind kind, String label, BigDecimal amount, String sourceType,
        Long sourceId) {

    public enum Kind {
        INCOME,
        EXPENSE
    }
}
