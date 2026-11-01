package app.logis.core.users;

import app.logis.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUsersTest extends IntegrationTest {

    private MockHttpSession adminSession() throws Exception {
        return login(createUser(Role.ADMIN, true));
    }

    private org.springframework.test.web.servlet.ResultActions patchJson(String url, Object body, MockHttpSession s)
            throws Exception {
        return mvc.perform(patch(url).with(csrf()).session(s)
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    // --- 6.1 ----------------------------------------------------------------------------------

    @Test
    void userRoleCannotUseAdminApi() throws Exception {
        var session = login(createUser(Role.USER, true));
        getWith("/api/admin/users", session).andExpect(status().isForbidden());
    }

    @Test
    void createUserRequiresPasswordChangeAndTotpSetup() throws Exception {
        var admin = adminSession();
        var name = "anna" + System.nanoTime();
        postJson("/api/admin/users", Map.of("username", name, "displayName", "Anna", "role", "USER",
                "password", "start-passwort-123"), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.totpEnabled").value(false));

        var created = users.findByUsernameIgnoreCase(name).orElseThrow();
        assertThat(created.isPasswordChangeRequired()).isTrue();

        var anna = new TestUser(created.getId(), name, "start-passwort-123", null);
        var session = passwordStep(anna);
        getWith("/api/auth/me", session).andExpect(jsonPath("$.stage").value("TOTP_ENROLLMENT_REQUIRED"));
    }

    @Test
    void duplicateUsernameIgnoringCase() throws Exception {
        var admin = adminSession();
        var name = "anna" + System.nanoTime();
        var body = new HashMap<String, Object>(Map.of("username", name, "role", "USER", "password", "start-passwort-123"));
        postJson("/api/admin/users", body, admin).andExpect(status().isCreated());
        body.put("username", name.toUpperCase());
        postJson("/api/admin/users", body, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Benutzername ist bereits vergeben"));
    }

    @Test
    void listDoesNotLeakSecrets() throws Exception {
        createUser(Role.USER, true);
        getWith("/api/admin/users", adminSession())
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("bcrypt"))))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("totpSecret"))));
    }

    @Test
    void roleChangeTakesEffectOnNextRequest() throws Exception {
        var user = createUser(Role.USER, true);
        var userSession = login(user);
        getWith("/api/admin/users", userSession).andExpect(status().isForbidden());

        patchJson("/api/admin/users/" + user.id(), Map.of("role", "ADMIN"), adminSession()).andExpect(status().isOk());
        getWith("/api/admin/users", userSession).andExpect(status().isOk());
    }

    // --- 6.2 ----------------------------------------------------------------------------------

    @Test
    void deactivateEndsSessionAndBlocksLogin() throws Exception {
        var user = createUser(Role.USER, true);
        var userSession = login(user);
        var admin = adminSession();

        patchJson("/api/admin/users/" + user.id(), Map.of("enabled", false), admin).andExpect(status().isOk());
        getWith("/api/modules", userSession).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", user.username(), "password", user.password()), null)
                .andExpect(status().isUnauthorized());

        patchJson("/api/admin/users/" + user.id(), Map.of("enabled", true), admin).andExpect(status().isOk());
        login(user);
    }

    // --- 6.3 ----------------------------------------------------------------------------------

    @Test
    void resetPasswordForcesChange() throws Exception {
        var user = createUser(Role.USER, true);
        var userSession = login(user);
        postJson("/api/admin/users/" + user.id() + "/reset-password", Map.of("password", "neues-start-pw-1"),
                adminSession()).andExpect(status().isOk());

        getWith("/api/modules", userSession).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", user.username(), "password", user.password()), null)
                .andExpect(status().isUnauthorized());

        var reset = new TestUser(user.id(), user.username(), "neues-start-pw-1", user.totpSecret());
        clock.advance(java.time.Duration.ofSeconds(31));
        var session = passwordStep(reset);
        postJson("/api/auth/totp", Map.of("code", totp(user.totpSecret())), session)
                .andExpect(jsonPath("$.stage").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void resetTotpForcesReenrollmentAndKillsRecoveryCodes() throws Exception {
        var user = createUser(Role.USER, true);
        var userSession = login(user);
        var codesJson = postJson("/api/account/recovery-codes", Map.of(), userSession)
                .andReturn().getResponse().getContentAsString();
        String recovery = com.jayway.jsonpath.JsonPath.read(codesJson, "$.recoveryCodes[0]");

        postJson("/api/admin/users/" + user.id() + "/reset-totp", Map.of(), adminSession()).andExpect(status().isOk());
        getWith("/api/modules", userSession).andExpect(status().isUnauthorized());

        var session = passwordStep(user);
        getWith("/api/auth/me", session).andExpect(jsonPath("$.stage").value("TOTP_ENROLLMENT_REQUIRED"));
        postJson("/api/auth/totp", Map.of("code", recovery), session).andExpect(status().isUnauthorized());
    }

    // --- 6.4 ----------------------------------------------------------------------------------

    @Test
    void lastActiveAdminIsProtected() throws Exception {
        // Disable all other admins, so that exactly one remains.
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).forEach(u -> {
            u.setEnabled(false);
            users.save(u);
        });
        var admin = createUser(Role.ADMIN, true);
        var session = login(admin);

        patchJson("/api/admin/users/" + admin.id(), Map.of("role", "USER"), session)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Es muss mindestens ein aktiver Admin bestehen bleiben"));
    }

    @Test
    void adminCannotDeactivateSelf() throws Exception {
        var admin = createUser(Role.ADMIN, true);
        createUser(Role.ADMIN, true);
        var session = login(admin);
        patchJson("/api/admin/users/" + admin.id(), Map.of("enabled", false), session)
                .andExpect(status().isBadRequest());
    }
}
