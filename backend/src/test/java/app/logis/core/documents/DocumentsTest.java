package app.logis.core.documents;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(DocumentsTest.StubOwnerConfig.class)
class DocumentsTest extends IntegrationTest {

    /** Owner IDs ending in 2 are read-only, ending in 3 invisible, all others readable and writable. */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubOwnerConfig {
        @Bean
        DocumentOwnerType stubOwner() {
            return new DocumentOwnerType() {
                public String type() {
                    return "test.stub";
                }

                public boolean canRead(Long ownerId, Long userId) {
                    return ownerId % 10 != 3;
                }

                public boolean canWrite(Long ownerId, Long userId) {
                    return ownerId % 10 != 2;
                }
            };
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong OWNERS = new java.util.concurrent.atomic.AtomicLong(1000);

    /** A fresh owner ID ending in the given digit. */
    private static long owner(int lastDigit) {
        return OWNERS.getAndAdd(10) + lastDigit;
    }

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    Documents documents;

    @Test
    void uploadListDownloadDelete() throws Exception {
        var session = login(createUser(Role.USER, true));
        var owner = owner(1);
        var id = id(upload(session, "test.stub", owner, "C:\\Scans\\Rechnung März.pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Rechnung März.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf")));

        getWith("/api/documents?ownerType=test.stub&ownerId=" + owner, session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sizeBytes").value(PDF.length));
        getWith("/api/documents/" + id, session)
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(PDF));

        mvc.perform(delete("/api/documents/" + id).with(csrf()).session(session))
                .andExpect(status().isNoContent());
        getWith("/api/documents/" + id, session).andExpect(status().isNotFound());
    }

    @Test
    void recognisesImagesBySignature() throws Exception {
        var session = login(createUser(Role.USER, true));
        upload(session, "test.stub", owner(1), "foto.bild", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
        var heic = new byte[16];
        System.arraycopy("ftypheic".getBytes(StandardCharsets.US_ASCII), 0, heic, 4, 8);
        upload(session, "test.stub", owner(1), "IMG_0001.HEIC", heic)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/heic"));
    }

    @Test
    void rejectsLargeAndUnknownFiles() throws Exception {
        var session = login(createUser(Role.USER, true));
        var large = Arrays.copyOf(PDF, 12 * 1024 * 1024);
        upload(session, "test.stub", owner(1), "gross.pdf", large)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nur PDF- oder Bilddateien bis 10 MB"));
        // A Word file (ZIP signature) renamed to .pdf.
        upload(session, "test.stub", owner(1), "brief.pdf", new byte[] {'P', 'K', 3, 4, 20, 0})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nur PDF- oder Bilddateien bis 10 MB"));
    }

    @Test
    void accessFollowsTheOwnerType() throws Exception {
        var session = login(createUser(Role.USER, true));
        var invisible = owner(3);
        var readOnly = owner(2);
        upload(session, "unknown.type", owner(1), "a.pdf", PDF).andExpect(status().isNotFound());
        upload(session, "test.stub", invisible, "a.pdf", PDF).andExpect(status().isNotFound());
        upload(session, "test.stub", readOnly, "a.pdf", PDF).andExpect(status().isForbidden());
        getWith("/api/documents?ownerType=test.stub&ownerId=" + invisible, session).andExpect(status().isNotFound());
        getWith("/api/documents?ownerType=test.stub&ownerId=" + readOnly, session).andExpect(status().isOk());
    }

    @Test
    void deleteAllRemovesTheOwnersDocuments() throws Exception {
        var session = login(createUser(Role.USER, true));
        var ownerId = owner(1);
        upload(session, "test.stub", ownerId, "a.pdf", PDF).andExpect(status().isCreated());
        assertThat(documents.count("test.stub", ownerId)).isEqualTo(1);
        documents.deleteAll("test.stub", ownerId);
        assertThat(documents.count("test.stub", ownerId)).isZero();
    }

    private ResultActions upload(MockHttpSession session, String ownerType, long ownerId, String name, byte[] bytes)
            throws Exception {
        return mvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .param("ownerType", ownerType)
                .param("ownerId", String.valueOf(ownerId))
                .with(csrf()).session(session));
    }

    private static Long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
