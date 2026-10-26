package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountTest extends IntegrationTest {

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        var session = login(createUser(Role.USER, true));
        postJson("/api/account/password", Map.of("currentPassword", "falsch-falsch", "newPassword", "neues-passwort-1"),
                session)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das aktuelle Passwort ist falsch."));
    }

    @Test
    void passwordChangeEndsOtherSessionsOnly() throws Exception {
        var user = createUser(Role.USER, true);
        var first = login(user);
        var second = login(user);

        postJson("/api/account/password", Map.of("currentPassword", user.password(), "newPassword", "neues-passwort-1"),
                first).andExpect(status().isOk());

        getWith("/api/modules", first).andExpect(status().isOk());
        getWith("/api/modules", second).andExpect(status().isUnauthorized());
    }

    @Test
    void regeneratingRecoveryCodesInvalidatesOldOnes() throws Exception {
        var user = createUser(Role.USER, true);
        var session = login(user);
        List<String> oldCodes = JsonPath.read(postJson("/api/account/recovery-codes", Map.of(), session)
                .andReturn().getResponse().getContentAsString(), "$.recoveryCodes");
        postJson("/api/account/recovery-codes", Map.of(), session)
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)));

        clock.advance(Duration.ofSeconds(31));
        var login = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", oldCodes.get(0)), login).andExpect(status().isUnauthorized());
    }
}
