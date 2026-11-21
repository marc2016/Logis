package app.logis.modules.longtermrental;

import app.logis.core.realestate.RealestateTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for long-term rental tests: long-term units and tenancies relative to the test clock. */
abstract class LongTermRentalTest extends RealestateTest {

    static final String TENANCIES = "/api/longtermrental/tenancies";

    protected LocalDate today() {
        return LocalDate.now(clock);
    }

    /** A property with the long-term units „Whg. 1“ and „Whg. 2“; returns property ID and both unit IDs. */
    protected long[] rentalHouse(Actor actor) throws Exception {
        var propertyId = id(createProperty(actor, Map.of("name", "Haus " + System.nanoTime(), "multiUnit", true,
                "firstUnit", Map.of("name", "Whg. 1", "usage", "LONG_TERM_RENTAL"))).andExpect(status().isCreated()));
        postJson("/api/properties/" + propertyId + "/units", Map.of("name", "Whg. 2", "usage", "LONG_TERM_RENTAL"),
                actor.session()).andExpect(status().isCreated());
        return new long[] {propertyId, unitId(propertyId, "Whg. 1"), unitId(propertyId, "Whg. 2")};
    }

    protected Map<String, Object> tenant(String name) {
        return Map.of("name", name, "street", "Musterweg 1", "postalCode", "00000", "city", "Talstadt",
                "phone", "+49 000 0000", "email", "mieter@example.org");
    }

    protected Map<String, Object> body(long unitId, LocalDate start, LocalDate end, String coldRent) {
        var body = new HashMap<String, Object>();
        body.put("unitId", unitId);
        body.put("startDate", start.toString());
        body.put("endDate", end == null ? null : end.toString());
        body.put("tenants", List.of(tenant("Erika Musterfrau")));
        body.put("rent", Map.of("coldRent", coldRent, "utilitiesAdvance", "150.00"));
        return body;
    }

    protected ResultActions create(Actor actor, long unitId, LocalDate start, LocalDate end) throws Exception {
        return postJson(TENANCIES, body(unitId, start, end, "650.00"), actor.session());
    }

    protected Long tenancy(Actor actor, long unitId, LocalDate start, LocalDate end) throws Exception {
        return id(create(actor, unitId, start, end).andExpect(status().isCreated()));
    }

    protected ResultActions deleteWith(String url, MockHttpSession session) throws Exception {
        return mvc.perform(delete(url).with(csrf()).session(session));
    }

    /** Moves the test clock and signs the user in again, as sessions do not live that long. */
    protected Actor later(Actor actor, int days) throws Exception {
        clock.advance(Duration.ofDays(days));
        return new Actor(actor.user(), login(actor.user()));
    }
}
