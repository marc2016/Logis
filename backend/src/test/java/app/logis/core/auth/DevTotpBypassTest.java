package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With the development flag, a correct password completes the sign-in (authentication spec). */
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "logis.auth.dev-totp-bypass=true",
        // Keep the test key and admin, which the dev profile would replace.
        "logis.encryption-key=dGVzdC1rZXktMzItYnl0ZXMtbG9uZy0xMjM0NTY3OCE=",
        "logis.admin.username=bootstrap-admin",
        "logis.admin.password=bootstrap-admin-password",
        "logis.dev.seed-file="})
class DevTotpBypassTest extends IntegrationTest {

    private org.springframework.test.web.servlet.ResultActions loginWith(String username, String password)
            throws Exception {
        return postJson("/api/auth/login", Map.of("username", username, "password", password), null);
    }

    @Test
    void passwordAloneSignsIn() throws Exception {
        var user = createUser(Role.USER, true);
        var result = loginWith(user.username(), user.password()).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        getWith("/api/modules", session).andExpect(status().isOk());
    }

    @Test
    void userWithoutTotpIsSignedInWithoutSetup() throws Exception {
        var user = createUser(Role.USER, false);
        loginWith(user.username(), user.password()).andExpect(jsonPath("$.stage").value("AUTHENTICATED"));
    }

    @Test
    void startPasswordMustStillBeChanged() throws Exception {
        var user = createUser(Role.USER, true, true);
        loginWith(user.username(), user.password())
                .andExpect(jsonPath("$.stage").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        var user = createUser(Role.USER, true);
        loginWith(user.username(), "wrong-password-123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Benutzername oder Passwort ist falsch"));
    }
}
