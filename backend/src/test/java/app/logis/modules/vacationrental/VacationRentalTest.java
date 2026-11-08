package app.logis.modules.vacationrental;

import app.logis.core.realestate.RealestateTest;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

/** Helpers for vacation rental tests. */
abstract class VacationRentalTest extends RealestateTest {

    protected Map<String, Object> stay(Long unitId, String arrival, String departure) {
        var body = new HashMap<String, Object>();
        body.put("unitId", unitId);
        body.put("arrival", arrival);
        body.put("departure", departure);
        body.put("adults", 2);
        return body;
    }

    protected ResultActions book(Actor actor, Map<String, Object> body) throws Exception {
        return postJson("/api/vacationrental/bookings", body, actor.session());
    }

    protected Long booking(Actor actor, Long unitId, String arrival, String departure) throws Exception {
        return id(book(actor, stay(unitId, arrival, departure))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    /** Creates an agency in the shared list; names get a unique suffix, because the list outlives each test. */
    protected Long agency(Actor actor, String name) throws Exception {
        return id(postJson("/api/vacationrental/agencies", Map.of("name", unique(name)), actor.session())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    protected static String unique(String name) {
        return name + " " + Long.toString(System.nanoTime(), 36);
    }

    /** Settles a booking directly in the database (independent of the statement API). */
    protected void settleDirectly(Long unitId, Long bookingId) {
        var statementId = jdbc.queryForObject("INSERT INTO vacationrental.statements "
                + "(unit_id, provider, period_from, period_to, statement_date) "
                + "VALUES (?, 'Test', DATE '2026-01-01', DATE '2026-12-31', DATE '2026-12-31') RETURNING id",
                Long.class, unitId);
        jdbc.update("INSERT INTO vacationrental.statement_lines (booking_id, statement_id, gross) VALUES (?, ?, 100)",
                bookingId, statementId);
    }
}
