package app.logis.modules.vacationrental;

import app.logis.core.realestate.UnitReferences;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Blocks deleting a property whose units still have bookings or statements. Deliberately active even when the
 * module is disabled: its data is kept then and must not lose its unit.
 */
@Component
class BookingUnitReferences implements UnitReferences {

    private final BookingRepository bookings;
    private final StatementRepository statements;

    BookingUnitReferences(BookingRepository bookings, StatementRepository statements) {
        this.bookings = bookings;
        this.statements = statements;
    }

    @Override
    public String blockingReason(Collection<Long> unitIds) {
        if (unitIds.isEmpty()) {
            return null;
        }
        return bookings.existsByUnitIdIn(unitIds) || statements.existsByUnitIdIn(unitIds)
                ? "Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden" : null;
    }
}
