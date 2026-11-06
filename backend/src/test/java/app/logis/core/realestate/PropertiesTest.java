package app.logis.core.realestate;

import app.logis.core.sharing.ShareRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertiesTest extends RealestateTest {

    @Autowired
    UnitAccess unitAccess;

    // --- Create, units ------------------------------------------------------------------------

    @Test
    void singleUnitPropertyGetsItsUnitAndOwner() throws Exception {
        var anna = actor();
        var id = singleUnitProperty(anna, "Ferienhaus Ostsee");

        getWith("/api/properties/" + id, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ferienhaus Ostsee"))
                .andExpect(jsonPath("$.multiUnit").value(false))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.units", hasSize(1)))
                .andExpect(jsonPath("$.units[0].name").value("Ferienhaus Ostsee"))
                .andExpect(jsonPath("$.units[0].usage").value("VACATION_RENTAL"));
        getWith("/api/properties", anna.session()).andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
    }

    @Test
    void someoneElsesLegalEntityIsNotFound() throws Exception {
        var anna = actor();
        var bert = actor();
        postJson("/api/properties", Map.of("legalEntityId", personalEntity(anna), "name", "X", "multiUnit", false,
                "firstUnit", Map.of("usage", "OTHER")), bert.session())
                .andExpect(status().isNotFound());
    }

    @Test
    void addingASecondUnitMakesItMultiUnit() throws Exception {
        var anna = actor();
        var id = singleUnitProperty(anna, "Ferienhaus Ostsee");
        var firstUnit = unitIds(id).getFirst();

        postJson("/api/properties/" + id + "/units", Map.of("name", "Einliegerwohnung", "usage", "VACATION_RENTAL"),
                anna.session())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.multiUnit").value(true))
                .andExpect(jsonPath("$.units", hasSize(2)))
                .andExpect(jsonPath("$.units[0].id").value(firstUnit));

        postJson("/api/properties/" + id + "/units", Map.of("name", "einliegerwohnung", "usage", "OTHER"),
                anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Eine Einheit mit diesem Namen existiert bereits"));
    }

    @Test
    void lastActiveUnitCannotBeArchived() throws Exception {
        var anna = actor();
        var id = singleUnitProperty(anna, "Ferienhaus Ostsee");
        var unit = unitIds(id).getFirst();

        postJson("/api/units/" + unit + "/archive", Map.of(), anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ein Objekt braucht mindestens eine aktive Einheit"));

        postJson("/api/properties/" + id + "/units", Map.of("name", "Neu", "usage", "OTHER"), anna.session());
        postJson("/api/units/" + unit + "/archive", Map.of(), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.units[0].archived").value(true));
    }

    // --- Edit, archive, delete -----------------------------------------------------------------

    @Test
    void editorMayManageUnitsButNotTheProperty() throws Exception {
        var anna = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "property", ids[0], bert, "EDITOR");

        putJson("/api/properties/" + ids[0], Map.of("name", "Umbenannt"), bert.session())
                .andExpect(status().isForbidden());
        postJson("/api/properties/" + ids[0] + "/archive", Map.of(), bert.session())
                .andExpect(status().isForbidden());
        putJson("/api/units/" + ids[1], Map.of("name", "Whg. 1 Meerblick", "usage", "VACATION_RENTAL"), bert.session())
                .andExpect(status().isOk());
    }

    @Test
    void ownerEditsArchivesAndDeletes() throws Exception {
        var anna = actor();
        var id = singleUnitProperty(anna, "Altbau");

        putJson("/api/properties/" + id, Map.of("name", "Altbau Mitte", "street", "Hauptstr. 1",
                "postalCode", "12345", "city", "Berlin"), anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Altbau Mitte"))
                .andExpect(jsonPath("$.city").value("Berlin"));

        postJson("/api/properties/" + id + "/archive", Map.of(), anna.session()).andExpect(status().isOk());
        getWith("/api/properties", anna.session()).andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
        getWith("/api/properties?includeArchived=true", anna.session())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        mvc.perform(delete("/api/properties/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.object_shares WHERE object_type = 'property' "
                + "AND object_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void deleteIsRefusedWithAttributedTransactions() throws Exception {
        var anna = actor();
        var id = singleUnitProperty(anna, "Ferienhaus");
        var account = account(anna, "Giro");
        var tx = transaction(anna, Map.of("accountId", account, "amount", "95.00", "bookingDate", "2026-10-03"));
        jdbc.update("UPDATE core.transactions SET unit_id = ? WHERE id = ?", unitIds(id).getFirst(), tx);

        mvc.perform(delete("/api/properties/" + id).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Dem Objekt sind noch Transaktionen zugeordnet und es kann nur archiviert werden"));
    }

    @Test
    void legalEntityWithPropertiesCannotBeDeleted() throws Exception {
        var anna = actor();
        var entity = id(postJson("/api/legal-entities", Map.of("name", "GbR Ostsee", "kind", "GBR"), anna.session())
                .andExpect(status().isCreated()));
        createProperty(anna, Map.of("legalEntityId", entity, "name", "Haus", "multiUnit", false,
                "firstUnit", Map.of("usage", "OTHER"))).andExpect(status().isCreated());

        mvc.perform(delete("/api/legal-entities/" + entity).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der Rechtsträger hat noch Objekte"));
    }

    // --- Visibility ----------------------------------------------------------------------------

    @Test
    void unitOnlyAccessSeesTheFrame() throws Exception {
        var anna = actor();
        var carla = actor();
        var dieter = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");
        share(anna, "property", ids[0], dieter, "VIEWER");
        share(anna, "unit", ids[1], dieter, "EDITOR");

        getWith("/api/properties/" + ids[0], anna.session())
                .andExpect(jsonPath("$.units", hasSize(2)));

        getWith("/api/properties/" + ids[0], carla.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").doesNotExist())
                .andExpect(jsonPath("$.legalEntity.name").exists())
                .andExpect(jsonPath("$.units", hasSize(1)))
                .andExpect(jsonPath("$.units[0].name").value("Whg. 2"))
                .andExpect(jsonPath("$.units[0].myRole").value("EDITOR"));
        getWith("/api/properties", carla.session()).andExpect(jsonPath("$[?(@.id == " + ids[0] + ")]").exists());
        getWith("/api/shares/property/" + ids[0], carla.session()).andExpect(status().isNotFound());
        putJson("/api/properties/" + ids[0], Map.of("name", "X"), carla.session()).andExpect(status().isForbidden());
        putJson("/api/units/" + ids[2], Map.of("name", "X", "usage", "OTHER"), carla.session())
                .andExpect(status().isForbidden());

        getWith("/api/properties/" + ids[0], dieter.session())
                .andExpect(jsonPath("$.myRole").value("VIEWER"))
                .andExpect(jsonPath("$.units[0].myRole").value("EDITOR"))
                .andExpect(jsonPath("$.units[1].myRole").value("VIEWER"));

        assertThat(unitAccess.roleOn(ids[1], dieter.id())).contains(ShareRole.EDITOR);
        assertThat(unitAccess.units(dieter.id(), ShareRole.VIEWER))
                .containsEntry(ids[1], ShareRole.EDITOR).containsEntry(ids[2], ShareRole.VIEWER);
        assertThat(unitAccess.units(carla.id(), ShareRole.VIEWER)).containsOnlyKeys(ids[2]);
        assertThat(unitAccess.roleOn(ids[1], carla.id())).isEmpty();

        getWith("/api/properties/" + ids[0], bert.session()).andExpect(status().isNotFound());
        getWith("/api/properties", bert.session()).andExpect(jsonPath("$[?(@.id == " + ids[0] + ")]").doesNotExist());
    }

    @Test
    void unitSharesAreManagedByPropertyOwners() throws Exception {
        var anna = actor();
        var carla = actor();
        var bert = actor();
        var ids = twoUnitProperty(anna);
        share(anna, "unit", ids[2], carla, "EDITOR");

        getWith("/api/shares/unit/" + ids[2], anna.session())
                .andExpect(jsonPath("$.canManage").value(true));
        getWith("/api/shares/unit/" + ids[2], carla.session())
                .andExpect(jsonPath("$.myRole").value("EDITOR"))
                .andExpect(jsonPath("$.canManage").value(false));
        postJson("/api/shares/unit/" + ids[2], Map.of("userId", bert.id(), "role", "VIEWER"), carla.session())
                .andExpect(status().isForbidden());
        postJson("/api/shares/unit/" + ids[2], Map.of("userId", bert.id(), "role", "OWNER"), anna.session())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Für Einheiten gibt es nur die Rollen Lesen und Bearbeiten"));

        mvc.perform(delete("/api/shares/unit/" + ids[2] + "/" + carla.id()).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        getWith("/api/properties/" + ids[0], carla.session()).andExpect(status().isNotFound());
    }
}
