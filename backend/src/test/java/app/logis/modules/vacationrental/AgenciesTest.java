package app.logis.modules.vacationrental;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgenciesTest extends VacationRentalTest {

    private static final String BASE = "/api/vacationrental/agencies";

    private Map<String, Object> find(Actor actor, Long id) throws Exception {
        List<Map<String, Object>> all = JsonPath.read(getWith(BASE, actor.session()).andReturn().getResponse()
                .getContentAsString(), "$");
        return all.stream().filter(a -> ((Number) a.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    private long version(Actor actor, Long id) throws Exception {
        return ((Number) find(actor, id).get("version")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions rename(Actor actor, Long id, String name, long version)
            throws Exception {
        return mvc.perform(put(BASE + "/" + id).with(csrf()).session(actor.session()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", name, "version", version))));
    }

    @Test
    void sharedListWithUniqueNames() throws Exception {
        var anna = actor();
        var bert = actor();
        var name = unique("Ferienbüro Nord");
        var id = id(postJson(BASE, Map.of("name", name), anna.session()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.aliases[0].alias").value(name)));

        // Bert sees the same agency.
        assertThat(find(bert, id).get("name")).isEqualTo(name);

        postJson(BASE, Map.of("name", name.toUpperCase()), bert.session()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ein Vermittler mit diesem Namen existiert bereits"));
        postJson(BASE, Map.of("name", " "), bert.session()).andExpect(status().isBadRequest());
    }

    @Test
    void renameKeepsTheOldNameAsMailName() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var longName = unique("VVV-Stadt- und Citymarketing Nordhorn e.V.");
        var id = id(postJson(BASE, Map.of("name", longName), anna.session()));
        var body = stay(unit, "2027-05-15", "2027-05-18");
        body.put("agencyId", id);
        body.put("externalRef", "6a29b55l9j");
        var bookingId = id(book(anna, body).andExpect(status().isCreated()));

        var shortName = unique("VVV Nordhorn");
        rename(anna, id, shortName, version(anna, id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(shortName))
                .andExpect(jsonPath("$.aliases[*].alias", hasItem(longName)))
                .andExpect(jsonPath("$.aliases[*].alias", hasItem(shortName)))
                .andExpect(jsonPath("$.bookingCount").value(1));
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.agency.name").value(shortName));
        // Booking numbers stay unique per agency identity, whatever its name.
        var again = stay(unit, "2027-06-15", "2027-06-18");
        again.put("agencyId", id);
        again.put("externalRef", "6A29B55L9J");
        book(anna, again).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Buchungsnummer ist bereits erfasst"));

        // A stale version is rejected.
        rename(anna, id, unique("Anders"), 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Vermittler wurde inzwischen geändert"));
    }

    @Test
    void archivedAgenciesStayOnBookings() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var airbnb = agency(anna, "Airbnb");
        var body = stay(unit, "2027-06-01", "2027-06-03");
        body.put("agencyId", airbnb);
        var bookingId = id(book(anna, body));

        postJson(BASE + "/" + airbnb + "/archive", Map.of("version", version(anna, airbnb)), anna.session())
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.agency.archived").value(true));
        postJson(BASE + "/" + airbnb + "/restore", Map.of("version", version(anna, airbnb)), anna.session())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void mailNamesAreUniqueAcrossAgencies() throws Exception {
        var anna = actor();
        var fewo = agency(anna, "Fewo & Meer");
        var holidu = agency(anna, "Holidu");
        var mailName = unique("Fewo und Meer");
        postJson(BASE + "/" + fewo + "/aliases", Map.of("alias", mailName), anna.session())
                .andExpect(status().isOk()).andExpect(jsonPath("$.aliases[*].alias", hasItem(mailName)));
        postJson(BASE + "/" + holidu + "/aliases", Map.of("alias", mailName.toLowerCase()), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Dieser Name gehört bereits zu einem anderen Vermittler"));

        // The agency's own name cannot be removed, another mail name can.
        List<Map<String, Object>> aliases = JsonPath.read(json.writeValueAsString(find(anna, fewo)), "$.aliases");
        var own = aliases.stream().filter(a -> !a.get("alias").equals(mailName)).findFirst().orElseThrow();
        var extra = aliases.stream().filter(a -> a.get("alias").equals(mailName)).findFirst().orElseThrow();
        mvc.perform(delete(BASE + "/" + fewo + "/aliases/" + own.get("id")).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict());
        mvc.perform(delete(BASE + "/" + fewo + "/aliases/" + extra.get("id")).with(csrf()).session(anna.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aliases[*].alias", not(hasItem(mailName))));
    }

    @Test
    void mergeMovesBookingsMappingsAndNames() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var target = agency(anna, "Holidu");
        var source = agency(anna, "holidu.de");
        var sourceName = (String) find(anna, source).get("name");
        var body = stay(unit, "2027-07-01", "2027-07-03");
        body.put("agencyId", source);
        body.put("externalRef", "A1");
        var bookingId = id(book(anna, body));
        jdbc.update("INSERT INTO vacationrental.unit_mappings (owner_user_id, agency_id, property_name, unit_id) "
                + "VALUES (?, ?, 'Ferienhaus Seeufer', ?), (?, ?, 'Ferienhaus Seeufer', ?), (?, ?, 'Anderes Haus', ?)",
                anna.id(), source, unit, anna.id(), target, unit, anna.id(), source, unit);

        postJson(BASE + "/" + source + "/merge", Map.of("targetId", target), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.intValue()))
                .andExpect(jsonPath("$.aliases[*].alias", hasItem(sourceName)));
        getWith("/api/vacationrental/bookings/" + bookingId, anna.session())
                .andExpect(jsonPath("$.agency.id").value(target.intValue()));
        getWith(BASE, anna.session()).andExpect(jsonPath("$[*].id", not(hasItem(source.intValue()))));
        assertThat(jdbc.queryForList("SELECT property_name FROM vacationrental.unit_mappings "
                + "WHERE owner_user_id = ? ORDER BY property_name", String.class, anna.id()))
                .containsExactly("Anderes Haus", "Ferienhaus Seeufer");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.unit_mappings WHERE agency_id = ?",
                Long.class, source)).isZero();
    }

    @Test
    void mergeIsRejectedWhenBookingNumbersCollide() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var a = agency(anna, "Agentur A");
        var b = agency(anna, "Agentur B");
        for (var entry : Map.of(a, "2027-08-01", b, "2027-08-05").entrySet()) {
            var body = stay(unit, entry.getValue(), entry.getValue().replace("-01", "-03").replace("-05", "-07"));
            body.put("agencyId", entry.getKey());
            body.put("externalRef", "1234");
            book(anna, body).andExpect(status().isCreated());
        }
        postJson(BASE + "/" + a + "/merge", Map.of("targetId", b), anna.session()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Eine Buchungsnummer kommt bei beiden Vermittlern auf derselben Einheit vor"));
    }

    @Test
    void onlyUnusedAgenciesCanBeDeleted() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var used = agency(anna, "Holidu");
        var body = stay(unit, "2027-09-01", "2027-09-03");
        body.put("agencyId", used);
        book(anna, body).andExpect(status().isCreated());
        mvc.perform(delete(BASE + "/" + used).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Vermittler wird noch verwendet und kann nur archiviert werden"));

        var unused = agency(anna, "Unbenutzt");
        mvc.perform(delete(BASE + "/" + unused).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith(BASE, anna.session()).andExpect(jsonPath("$[*].id", not(hasItem(unused.intValue()))));
    }

    @Test
    void bookingCountsOnlyCoverVisibleBookings() throws Exception {
        var anna = actor();
        var bert = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var id = agency(anna, "Zählung");
        var body = stay(unit, "2027-10-01", "2027-10-03");
        body.put("agencyId", id);
        book(anna, body).andExpect(status().isCreated());
        assertThat(((Number) find(anna, id).get("bookingCount")).intValue()).isEqualTo(1);
        assertThat(((Number) find(bert, id).get("bookingCount")).intValue()).isZero();
    }
}
