package app.logis.core.modules;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModulesEnabledTest extends IntegrationTest {

    @Test
    void listContainsActiveModule() throws Exception {
        mvc.perform(get("/api/modules").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'vacationrental')].label").value("Ferienvermietung"));
    }

    @Test
    void activeModuleEndpointIsReachable() throws Exception {
        mvc.perform(get("/api/vacationrental/status").with(user("u").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void listRequiresLogin() throws Exception {
        mvc.perform(get("/api/modules")).andExpect(status().isUnauthorized());
    }
}
