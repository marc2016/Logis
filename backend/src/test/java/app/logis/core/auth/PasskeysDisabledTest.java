package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without a public address there are no passkeys, and everything else works as before. */
class PasskeysDisabledTest extends IntegrationTest {

    @Test
    void noPasskeysWithoutPublicAddress() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.passkeys").value(false));
        mvc.perform(post("/api/auth/passkey/options").with(csrf())).andExpect(status().isNotFound());
        var anna = createUser(Role.USER, true);
        var session = login(anna);
        getWith("/api/account/passkeys", session).andExpect(status().isNotFound());
        postJson("/api/account/passkeys/registration/options", Map.of("password", anna.password()), session)
                .andExpect(status().isNotFound());
    }
}
