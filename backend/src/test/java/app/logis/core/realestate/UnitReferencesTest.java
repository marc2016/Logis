package app.logis.core.realestate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(UnitReferencesTest.StubConfig.class)
class UnitReferencesTest extends RealestateTest {

    static final Set<Long> REFERENCED = ConcurrentHashMap.newKeySet();

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfig {
        @Bean
        UnitReferences stubReferences() {
            return unitIds -> unitIds.stream().anyMatch(REFERENCED::contains)
                    ? "Zum Objekt gibt es noch Testdaten und es kann nur archiviert werden" : null;
        }
    }

    @Test
    void referencedUnitBlocksDelete() throws Exception {
        var anna = actor();
        var blocked = singleUnitProperty(anna, "Haus A");
        REFERENCED.add(unitIds(blocked).getFirst());
        var free = singleUnitProperty(anna, "Haus B");

        mvc.perform(delete("/api/properties/" + blocked).with(csrf()).session(anna.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Zum Objekt gibt es noch Testdaten und es kann nur archiviert werden"));
        mvc.perform(delete("/api/properties/" + free).with(csrf()).session(anna.session()))
                .andExpect(status().isNoContent());
    }
}
