package app.logis.core.realestate;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertyCostsTest extends RealestateTest {

    private Long category(String kind, String name) {
        return jdbc.queryForObject("SELECT id FROM core.categories WHERE kind = ? AND name = ? AND parent_id IS NULL",
                Long.class, kind, name);
    }

    private Map<String, Object> repair(Object amount) {
        var body = new HashMap<String, Object>();
        body.put("date", "2026-11-12");
        body.put("amount", amount);
        body.put("categoryId", category("EXPENSE", "Wohnen"));
        body.put("counterparty", "Handwerker Meyer");
        return body;
    }

    @Test
    void recordEditAndDeleteACost() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        var costId = id(postJson("/api/properties/" + property + "/costs", repair("320.00"), anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(320.00))
                .andExpect(jsonPath("$.unitId").doesNotExist())
                .andExpect(jsonPath("$.version").value(0)));

        var change = repair("340.00");
        change.put("version", 0);
        putJson("/api/costs/" + costId, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(340.00))
                .andExpect(jsonPath("$.version").value(1));
        // A second change based on the old version.
        putJson("/api/costs/" + costId, change, anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Kosten wurden inzwischen geändert"));

        mvc.perform(delete("/api/costs/" + costId).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith("/api/costs/" + costId, anna.session()).andExpect(status().isNotFound());
    }

    @Test
    void validatesAmountAndCategory() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        var url = "/api/properties/" + property + "/costs";
        postJson(url, repair("0"), anna.session()).andExpect(status().isBadRequest());
        postJson(url, repair("-5.00"), anna.session()).andExpect(status().isBadRequest());
        postJson(url, repair("1.234"), anna.session()).andExpect(status().isBadRequest());
        var income = repair("10.00");
        income.put("categoryId", category("INCOME", "Mieteinnahmen"));
        postJson(url, income, anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Kategorie ist nicht verfügbar"));
        var noAccount = repair("10.00");
        noAccount.remove("categoryId");
        postJson(url, noAccount, anna.session()).andExpect(status().isCreated());
    }

    @Test
    void rightsFollowTheTarget() throws Exception {
        var anna = actor();
        var carla = actor();
        var dieter = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");
        share(anna, "property", ids[0], dieter, "VIEWER");
        var url = "/api/properties/" + ids[0] + "/costs";

        // Viewer may not record; strangers do not see the property.
        postJson(url, repair("10.00"), dieter.session()).andExpect(status().isForbidden());
        postJson(url, repair("10.00"), bert.session()).andExpect(status().isNotFound());

        // Carla may record on "Whg. 2", not on the property itself and not on "Whg. 1".
        var onUnit = repair("50.00");
        onUnit.put("unitId", ids[2]);
        var carlasCost = id(postJson(url, onUnit, carla.session()).andExpect(status().isCreated()));
        postJson(url, repair("10.00"), carla.session()).andExpect(status().isForbidden());
        var otherUnit = repair("10.00");
        otherUnit.put("unitId", ids[1]);
        postJson(url, otherUnit, carla.session()).andExpect(status().isNotFound());

        // A property-level cost is invisible to Carla.
        var propertyCost = id(postJson(url, repair("20.00"), anna.session()).andExpect(status().isCreated()));
        getWith("/api/costs/" + propertyCost, carla.session()).andExpect(status().isNotFound());
        getWith("/api/costs/" + carlasCost, dieter.session()).andExpect(status().isOk());
        mvc.perform(delete("/api/costs/" + carlasCost).with(csrf()).session(dieter.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void receiptsOnCosts() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var costId = id(postJson("/api/properties/" + property + "/costs", repair("320.00"), anna.session())
                .andExpect(status().isCreated()));
        var pdf = new MockMultipartFile("file", "Rechnung.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));

        var docId = id(mvc.perform(multipart("/api/documents").file(pdf)
                        .param("ownerType", "realestate.cost").param("ownerId", costId.toString())
                        .with(csrf()).session(anna.session()))
                .andExpect(status().isCreated()));
        getWith("/api/documents/" + docId, dieter.session()).andExpect(status().isOk());
        mvc.perform(multipart("/api/documents").file(pdf)
                        .param("ownerType", "realestate.cost").param("ownerId", costId.toString())
                        .with(csrf()).session(dieter.session()))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/costs/" + costId).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith("/api/documents/" + docId, anna.session()).andExpect(status().isNotFound());
    }

    @Test
    void propertyWithCostsCannotBeDeleted() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        postJson("/api/properties/" + property + "/costs", repair("320.00"), anna.session())
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/properties/" + property).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Zum Objekt sind noch Kosten erfasst und es kann nur archiviert werden"));
    }
}
