package app.logis.modules.longtermrental;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantDataAndDocumentsTest extends LongTermRentalTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    private static final String DOCUMENTS = "/api/documents?ownerType=longtermrental.tenancy&ownerId=";

    private ResultActions attach(MockHttpSession session, Long tenancyId, String name) throws Exception {
        return mvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", PNG))
                .param("ownerType", "longtermrental.tenancy").param("ownerId", tenancyId.toString())
                .with(csrf()).session(session));
    }

    @Test
    void tenantDataIsDeletedOnlyAfterTheEnd() throws Exception {
        var anna = actor();
        var carla = actor();
        var house = rentalHouse(anna);
        share(anna, "property", house[0], carla, "VIEWER");
        var end = today().plusDays(1);
        var id = tenancy(anna, house[1], today().minusDays(30), end);
        var doc = id(attach(anna.session(), id, "Mietvertrag.png").andExpect(status().isCreated()));

        deleteWith(TENANCIES + "/" + id + "/tenant-data", anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Mieterdaten können erst nach dem Ende des Mietverhältnisses gelöscht werden."));
        // The last day still counts as running.
        anna = later(anna, 1);
        deleteWith(TENANCIES + "/" + id + "/tenant-data", anna.session()).andExpect(status().isConflict());

        anna = later(anna, 1);
        deleteWith(TENANCIES + "/" + id + "/tenant-data", login(carla.user())).andExpect(status().isForbidden());
        deleteWith(TENANCIES + "/" + id + "/tenant-data", anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantsDeleted").value(true))
                .andExpect(jsonPath("$.tenants", hasSize(0)))
                .andExpect(jsonPath("$.endDate").value(end.toString()))
                .andExpect(jsonPath("$.currentRent.coldRent").value("650.00"));
        assertThat(jdbc.queryForList("SELECT concat_ws('|', name, street, postal_code, city, country, phone, email) "
                + "FROM longtermrental.tenants WHERE tenancy_id = ?", String.class, id)).containsExactly("");

        // Documents stay; later changes keep the tenant data deleted.
        getWith("/api/documents/" + doc, anna.session()).andExpect(status().isOk());
        var change = body(house[1], today().minusDays(32), end, "0");
        change.put("tenants", List.of(tenant("Erika Musterfrau")));
        change.put("version", 1);
        putJson(TENANCIES + "/" + id, change, anna.session())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantsDeleted").value(true))
                .andExpect(jsonPath("$.tenants", hasSize(0)));
    }

    @Test
    void documentsAreForEditorsAndGoWithTheTenancy() throws Exception {
        var anna = actor();
        var carla = actor();
        var bert = actor();
        var house = rentalHouse(anna);
        share(anna, "property", house[0], carla, "VIEWER");
        var id = tenancy(anna, house[1], today(), null);

        var doc = id(attach(anna.session(), id, "Mietvertrag.png")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Mietvertrag.png")));
        getWith(DOCUMENTS + id, anna.session()).andExpect(jsonPath("$", hasSize(1)));
        getWith("/api/documents/" + doc, anna.session()).andExpect(status().isOk());

        getWith(DOCUMENTS + id, carla.session()).andExpect(status().isNotFound());
        getWith("/api/documents/" + doc, carla.session()).andExpect(status().isNotFound());
        attach(carla.session(), id, "b.png").andExpect(status().isNotFound());
        getWith("/api/documents/" + doc, bert.session()).andExpect(status().isNotFound());

        deleteWith(TENANCIES + "/" + id, anna.session()).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.documents WHERE id = ?", Integer.class, doc))
                .isZero();
    }

    @Test
    void propertyWithTenanciesCannotBeDeleted() throws Exception {
        var anna = actor();
        var house = rentalHouse(anna);
        var id = tenancy(anna, house[2], today().minusDays(60), today().minusDays(30));
        deleteWith("/api/properties/" + house[0], anna.session())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Objekt hat noch Mietverhältnisse"));
        deleteWith(TENANCIES + "/" + id, anna.session()).andExpect(status().isNoContent());
        deleteWith("/api/properties/" + house[0], anna.session()).andExpect(status().isNoContent());
    }
}
