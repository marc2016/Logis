package app.logis.modules.longtermrental;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "logis.modules.longtermrental.enabled=false")
class LongTermRentalDisabledTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void disabledModuleIsNeitherListedNorReachable() throws Exception {
        var session = login(createUser(Role.USER, true));
        getWith("/api/modules", session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'longtermrental')]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == 'vacationrental')]").isNotEmpty());
        getWith("/api/longtermrental/tenancies", session).andExpect(status().isNotFound());
        getWith("/api/longtermrental/units", session).andExpect(status().isNotFound());
        getWith("/api/documents?ownerType=longtermrental.tenancy&ownerId=1", session)
                .andExpect(status().is4xxClientError());
    }

    @Test
    void tablesExistWhileDisabled() {
        // Migrations run for disabled modules too, so the data is there when the module comes back.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM longtermrental.tenancies WHERE unit_id = -1",
                Integer.class)).isNotNull();
    }
}
