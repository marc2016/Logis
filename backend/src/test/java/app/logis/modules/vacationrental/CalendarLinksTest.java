package app.logis.modules.vacationrental;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarLinksTest extends VacationRentalTest {

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CalendarLinkRepository links;

    private JsonNode createLink(Actor actor, Long unitId, String name, boolean withGuests) throws Exception {
        var body = postJson("/api/vacationrental/calendar-links",
                Map.of("unitId", unitId, "name", name, "withGuests", withGuests), actor.session())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String feed(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andReturn().getResponse().getContentAsString();
    }

    private Long guestBooking(Actor actor, Long unitId, String arrival, String departure, String guest, String ref)
            throws Exception {
        var body = stay(unitId, arrival, departure);
        body.put("guest", Map.of("name", guest));
        body.put("externalRef", ref);
        return id(book(actor, body).andExpect(status().isCreated()));
    }

    @Test
    void tokenIsStoredOnlyAsHash() {
        var token = CalendarLink.newToken();
        var link = links.save(new CalendarLink(1L, 1L, "Test", false, token, Instant.now()));
        assertThat(token).hasSize(43);
        assertThat(link.getTokenHash()).isNotEqualTo(token).hasSize(64);
        assertThat(links.findByTokenHash(CalendarLink.hash(token))).isPresent();
        assertThat(links.findByTokenHash(CalendarLink.hash(CalendarLink.newToken()))).isEmpty();
    }

    @Test
    void createListAndRevoke() throws Exception {
        var anna = actor();
        var dieter = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var created = createLink(anna, unit, "Reinigung", false);
        var path = created.get("path").asText();
        assertThat(path).matches("/api/ical/[A-Za-z0-9_-]{43}\\.ics");

        getWith("/api/vacationrental/calendar-links?unitId=" + unit, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Reinigung"))
                .andExpect(jsonPath("$[0].withGuests").value(false))
                .andExpect(content().string(not(containsString(path.substring(10, 53)))));

        // Only the creator can revoke; others cannot see the unit at all.
        var linkId = created.get("link").get("id").asLong();
        mvc.perform(delete("/api/vacationrental/calendar-links/" + linkId).with(csrf()).session(dieter.session()))
                .andExpect(status().isNotFound());
        postJson("/api/vacationrental/calendar-links", Map.of("unitId", unit, "name", "X", "withGuests", true),
                dieter.session()).andExpect(status().isNotFound());

        feed(path);
        mvc.perform(delete("/api/vacationrental/calendar-links/" + linkId).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        mvc.perform(get(path)).andExpect(status().isNotFound());
        getWith("/api/vacationrental/calendar-links?unitId=" + unit, anna.session()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void viewerCanCreateLinkWithGuests() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var unit = unitIds(property).getFirst();
        assertThat(createLink(dieter, unit, "Ich", true).get("link").get("withGuests").asBoolean()).isTrue();
    }

    @Test
    void feedWithAndWithoutGuests() throws Exception {
        var anna = actor();
        var unit = unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst();
        var today = LocalDate.now();
        var arrival = today.plusDays(10);
        guestBooking(anna, unit, arrival.toString(), arrival.plusDays(2).toString(), "Erika Musterfrau", "275505");
        var cancelled = guestBooking(anna, unit, arrival.plusDays(5).toString(), arrival.plusDays(7).toString(),
                "Storno Gast", "999");
        postJson("/api/vacationrental/bookings/" + cancelled + "/cancel", Map.of(), anna.session())
                .andExpect(status().isOk());
        var old = today.minusMonths(13);
        guestBooking(anna, unit, old.toString(), old.plusDays(3).toString(), "Alter Gast", "111");

        var start = arrival.toString().replace("-", "");
        var endExclusive = arrival.plusDays(3).toString().replace("-", "");

        var plain = feed(createLink(anna, unit, "Reinigung", false).get("path").asText());
        assertThat(plain).startsWith("BEGIN:VCALENDAR\r\n").contains("X-WR-CALNAME:Reinigung")
                .contains("DTSTART;VALUE=DATE:" + start, "DTEND;VALUE=DATE:" + endExclusive, "SUMMARY:Belegt\r\n")
                .doesNotContain("Erika", "275505", "Storno", "Alter Gast");
        assertThat(plain.split("BEGIN:VEVENT")).hasSize(2);

        var full = feed(createLink(anna, unit, "Ich", true).get("path").asText());
        assertThat(full).contains("SUMMARY:Erika Musterfrau (275505)").doesNotContain("Storno", "Alter Gast");
    }

    @Test
    void feedStopsWhenCreatorLosesAccess() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var unit = unitIds(property).getFirst();
        var path = createLink(dieter, unit, "Dieter", false).get("path").asText();
        feed(path);
        mvc.perform(delete("/api/shares/property/" + property + "/" + dieter.id()).with(csrf()).session(anna.session()))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void unknownTokenLooksLikeRevoked() throws Exception {
        var body = mvc.perform(get("/api/ical/" + CalendarLink.newToken() + ".ics"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).isEmpty();
        assertThat(List.of(CalendarFeed.escape("a,b;c\\d\ne"))).containsExactly("a\\,b\\;c\\\\d\\ne");
    }
}
