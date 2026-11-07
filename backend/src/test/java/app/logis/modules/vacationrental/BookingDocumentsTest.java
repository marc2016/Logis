package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingDocumentsTest extends VacationRentalTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    private ResultActions attach(MockHttpSession session, Long bookingId, String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .param("ownerType", "vacationrental.booking").param("ownerId", bookingId.toString())
                .with(csrf()).session(session));
    }

    @Test
    void attachTheConfirmationAndRules() throws Exception {
        var anna = actor();
        var dieter = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        share(anna, "property", property, dieter, "VIEWER");
        var booking = booking(anna, unitIds(property).getFirst(), "2026-11-06", "2026-11-08");

        var doc = id(attach(anna.session(), booking, "Buchungsbestätigung.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Buchungsbestätigung.png")));
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + booking, anna.session())
                .andExpect(jsonPath("$", hasSize(1)));
        attach(anna.session(), booking, "brief.docx", new byte[] {'P', 'K', 3, 4})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nur PDF- oder Bilddateien bis 10 MB"));

        // Viewers see neither the list nor a single document.
        getWith("/api/documents?ownerType=vacationrental.booking&ownerId=" + booking, dieter.session())
                .andExpect(status().isNotFound());
        getWith("/api/documents/" + doc, dieter.session()).andExpect(status().isNotFound());
    }

    @Test
    void documentsStayWithGuestDeletionAndGoWithTheBooking() throws Exception {
        var anna = actor();
        var booking = booking(anna, unitIds(singleUnitProperty(anna, "Haus Seeufer")).getFirst(),
                "2026-11-06", "2026-11-08");
        var doc = id(attach(anna.session(), booking, "a.png", PNG).andExpect(status().isCreated()));

        mvc.perform(delete("/api/vacationrental/bookings/" + booking + "/guest").with(csrf()).session(anna.session()))
                .andExpect(status().isOk());
        getWith("/api/documents/" + doc, anna.session()).andExpect(status().isOk());

        mvc.perform(delete("/api/vacationrental/bookings/" + booking).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM core.documents WHERE id = ?", Integer.class, doc)).isZero();
    }

    @Test
    void propertyWithBookingsCannotBeDeleted() throws Exception {
        var anna = actor();
        var property = singleUnitProperty(anna, "Haus Seeufer");
        booking(anna, unitIds(property).getFirst(), "2026-11-06", "2026-11-08");
        mvc.perform(delete("/api/properties/" + property).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden"));
        var empty = singleUnitProperty(anna, "Haus ohne Buchungen");
        mvc.perform(delete("/api/properties/" + empty).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
        postJson("/api/properties/" + property + "/archive", Map.of(), anna.session()).andExpect(status().isOk());
    }
}
