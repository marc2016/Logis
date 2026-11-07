package app.logis.core.modules;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "logis.modules.vacationrental.enabled=false")
class ModulesDisabledTest extends IntegrationTest {

    @Test
    void disabledModuleIsNotListed() throws Exception {
        mvc.perform(get("/api/modules").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'vacationrental')]").isEmpty());
    }

    @Test
    void disabledModuleEndpointGives404() throws Exception {
        var session = login(createUser(app.logis.core.users.Role.USER, true));
        getWith("/api/vacationrental/bookings", session).andExpect(status().isNotFound());
        getWith("/api/vacationrental/occupancy/overview?unitId=1&year=2026", session)
                .andExpect(status().isNotFound());
    }
}
