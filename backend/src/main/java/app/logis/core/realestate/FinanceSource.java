package app.logis.core.realestate;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Contributes items of a module (e.g. vacation rental statements) to the finances of properties. The core passes
 * only units the user can see, so a source never checks rights itself.
 */
public interface FinanceSource {

    /** Items of the given units with a date between {@code from} and {@code to} (inclusive). */
    List<FinanceItem> items(Collection<Long> unitIds, LocalDate from, LocalDate to);
}
