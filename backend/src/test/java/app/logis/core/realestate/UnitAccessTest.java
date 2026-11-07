package app.logis.core.realestate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnitAccessTest extends RealestateTest {

    @Autowired
    UnitAccess unitAccess;

    @Test
    void summariesCarryNamePropertyUsageAndArchiveFlags() throws Exception {
        var anna = actor();
        var ids = twoUnitProperty(anna);
        postJson("/api/units/" + ids[2] + "/archive", Map.of(), anna.session()).andExpect(status().isOk());

        var summaries = unitAccess.summaries(List.of(ids[1], ids[2], -1L));

        assertThat(summaries).hasSize(2);
        var first = summaries.get(ids[1]);
        assertThat(first.name()).isEqualTo("Whg. 1");
        assertThat(first.propertyId()).isEqualTo(ids[0]);
        assertThat(first.propertyName()).startsWith("Haus ");
        assertThat(first.propertyMultiUnit()).isTrue();
        assertThat(first.usage()).isEqualTo(UnitUsage.VACATION_RENTAL);
        assertThat(first.archived()).isFalse();
        assertThat(summaries.get(ids[2]).archived()).isTrue();
        assertThat(unitAccess.summaries(List.of())).isEmpty();
    }
}
