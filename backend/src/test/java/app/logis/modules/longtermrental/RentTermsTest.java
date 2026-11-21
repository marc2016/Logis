package app.logis.modules.longtermrental;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RentTermsTest extends LongTermRentalTest {

    @Test
    void currentRentFollowsTheDate() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var start = today().minusDays(30);
        var id = tenancy(anna, house[1], start, null);
        var terms = TENANCIES + "/" + id + "/rent-terms";

        postJson(terms, Map.of("validFrom", today().plusDays(2).toString(), "coldRent", "680.00",
                "utilitiesAdvance", "150.00"), anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rentTerms", hasSize(2)))
                .andExpect(jsonPath("$.currentRent.coldRent").value("650.00"))
                .andExpect(jsonPath("$.version").value(1));

        anna = later(anna, 1);
        getWith(TENANCIES + "/" + id, anna.session()).andExpect(jsonPath("$.currentRent.coldRent").value("650.00"));
        anna = later(anna, 1);
        getWith(TENANCIES + "/" + id, anna.session())
                .andExpect(jsonPath("$.currentRent.coldRent").value("680.00"))
                .andExpect(jsonPath("$.currentRent.total").value("830.00"))
                .andExpect(jsonPath("$.rentTerms[0].coldRent").value("650.00"))
                .andExpect(jsonPath("$.rentTerms[1].coldRent").value("680.00"));
        getWith(TENANCIES + "?unitId=" + house[1], anna.session())
                .andExpect(jsonPath("$[0].currentRent.total").value("830.00"));
    }

    @Test
    void totalWithFurtherItems() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var body = body(house[1], today(), null, "650.00");
        body.put("rent", Map.of("coldRent", "650.00", "utilitiesAdvance", "150.00",
                "others", List.of(Map.of("label", "Stellplatz", "amount", "40.00"))));
        var id = id(postJson(TENANCIES, body, anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentRent.others[0].label").value("Stellplatz"))
                .andExpect(jsonPath("$.currentRent.others[0].amount").value("40.00"))
                .andExpect(jsonPath("$.currentRent.total").value("840.00")));

        // Stored as net amount with a tax rate of 0, ready for commercial tenancies.
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList(
                "SELECT i.kind || ' ' || i.net_amount || ' ' || i.tax_rate FROM longtermrental.rent_items i "
                        + "JOIN longtermrental.rent_terms t ON t.id = i.rent_term_id WHERE t.tenancy_id = ? "
                        + "ORDER BY i.position", String.class, id))
                .containsExactly("COLD_RENT 650.00 0.00", "UTILITIES_ADVANCE 150.00 0.00", "OTHER 40.00 0.00");

        postJson(TENANCIES + "/" + id + "/rent-terms", Map.of("validFrom", today().plusDays(1).toString(),
                "coldRent", "650.00", "others", List.of(Map.of("label", " ", "amount", "40.00"))), anna.session())
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeAndDeleteTerms() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var start = today().minusDays(30);
        var id = tenancy(anna, house[1], start, null);
        var terms = TENANCIES + "/" + id + "/rent-terms";
        var firstTerm = termId(anna, id, 0);

        postJson(terms, Map.of("validFrom", start.toString(), "coldRent", "680.00"), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Für dieses Datum gibt es bereits eine Miete"));
        postJson(terms, Map.of("validFrom", start.minusDays(1).toString(), "coldRent", "680.00"), anna.session())
                .andExpect(status().isBadRequest());
        postJson(terms, Map.of("coldRent", "680.00"), anna.session()).andExpect(status().isBadRequest());
        postJson(terms, Map.of("validFrom", start.plusDays(10).toString()), anna.session())
                .andExpect(status().isBadRequest());

        postJson(terms, Map.of("validFrom", start.plusDays(10).toString(), "coldRent", "680.00"), anna.session())
                .andExpect(status().isCreated());
        postJson(terms, Map.of("validFrom", start.plusDays(20).toString(), "coldRent", "700.00"), anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentRent.coldRent").value("700.00"))
                .andExpect(jsonPath("$.currentRent.utilitiesAdvance").value("0.00"));
        var second = termId(anna, id, 1);
        var third = termId(anna, id, 2);

        // Moving a term onto the date of another one is rejected; changing its amounts works.
        putJson(terms + "/" + third, Map.of("validFrom", start.plusDays(10).toString(), "coldRent", "700.00"),
                anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Für dieses Datum gibt es bereits eine Miete"));
        putJson(terms + "/" + third, Map.of("validFrom", start.plusDays(21).toString(), "coldRent", "710.00",
                "utilitiesAdvance", "160.00"), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentTerms[2].validFrom").value(start.plusDays(21).toString()))
                .andExpect(jsonPath("$.currentRent.total").value("870.00"));
        // The first term keeps the start as its date.
        putJson(terms + "/" + firstTerm, Map.of("validFrom", start.plusDays(5).toString(), "coldRent", "640.00"),
                anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentTerms[0].validFrom").value(start.toString()))
                .andExpect(jsonPath("$.rentTerms[0].coldRent").value("640.00"));

        deleteWith(terms + "/" + firstTerm, anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die erste Miete kann nicht gelöscht werden."));
        deleteWith(terms + "/" + second, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentTerms", hasSize(2)));
        deleteWith(terms + "/" + second, anna.session()).andExpect(status().isNotFound());

        // The start cannot move behind a later rent.
        var change = body(house[1], start.plusDays(21), null, "0");
        change.put("version", jdbc.queryForObject("SELECT version FROM longtermrental.tenancies WHERE id = ?",
                Long.class, id));
        putJson(TENANCIES + "/" + id, change, anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Eine spätere Miete gilt bereits vor dem neuen Beginn."));
    }

    @Test
    void endedAndFutureTenanciesShowTheRentOfTheirPeriod() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var ended = tenancy(anna, house[1], today().minusDays(60), today().minusDays(10));
        postJson(TENANCIES + "/" + ended + "/rent-terms",
                Map.of("validFrom", today().minusDays(30).toString(), "coldRent", "680.00"), anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("ENDED"))
                .andExpect(jsonPath("$.currentRent.coldRent").value("680.00"));
        var future = tenancy(anna, house[2], today().plusDays(10), null);
        postJson(TENANCIES + "/" + future + "/rent-terms",
                Map.of("validFrom", today().plusDays(40).toString(), "coldRent", "680.00"), anna.session())
                .andExpect(jsonPath("$.currentRent.coldRent").value("650.00"));
    }

    private Long termId(Actor actor, Long tenancyId, int index) throws Exception {
        var content = getWith(TENANCIES + "/" + tenancyId, actor.session()).andReturn().getResponse()
                .getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(content, "$.rentTerms[" + index + "].id")).longValue();
    }
}
