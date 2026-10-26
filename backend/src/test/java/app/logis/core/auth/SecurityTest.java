package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityTest extends IntegrationTest {

    @Test
    void apiRequiresSession() throws Exception {
        mvc.perform(get("/api/modules"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        var session = login(createUser(Role.USER, true));
        mvc.perform(post("/api/account/recovery-codes").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminApiRequiresAdminRole() throws Exception {
        var session = login(createUser(Role.USER, true));
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }
}
