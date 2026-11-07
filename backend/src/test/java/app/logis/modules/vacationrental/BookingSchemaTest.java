package app.logis.modules.vacationrental;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database itself prevents overlapping stays, also for writes that bypass the service. */
class BookingSchemaTest extends IntegrationTest {

    private static final AtomicLong UNITS = new AtomicLong(900_000);

    @Autowired
    JdbcTemplate jdbc;

    private void insert(long unitId, String arrival, String departure, boolean cancelled) {
        jdbc.update("INSERT INTO vacationrental.bookings (unit_id, arrival, departure, adults, cancelled) "
                + "VALUES (?, ?::date, ?::date, 2, ?)", unitId, arrival, departure, cancelled);
    }

    @Test
    void overlappingStaysAreRejected() {
        var unit = UNITS.incrementAndGet();
        insert(unit, "2026-11-06", "2026-11-08", false);
        assertThatThrownBy(() -> insert(unit, "2026-11-07", "2026-11-10", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void changeoverDayCancelledStaysAndOtherUnitsAreAllowed() {
        var unit = UNITS.incrementAndGet();
        insert(unit, "2026-11-06", "2026-11-08", false);
        assertThatCode(() -> {
            insert(unit, "2026-11-08", "2026-11-10", false);
            insert(unit, "2026-11-07", "2026-11-09", true);
            insert(UNITS.incrementAndGet(), "2026-11-07", "2026-11-09", false);
        }).doesNotThrowAnyException();
    }
}
