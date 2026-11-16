package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Passkeys with a software authenticator against the real WebAuthn verification (authentication spec). */
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class PasskeysTest extends IntegrationTest {

    private static final String ORIGIN = "https://logis.example.org";
    private static final String FAILED = "Anmeldung mit Passkey fehlgeschlagen";

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Adds a passkey for the signed-in user; returns the device. */
    private SoftwareAuthenticator addPasskey(TestUser user, MockHttpSession session, String name) throws Exception {
        var device = new SoftwareAuthenticator(ORIGIN);
        var options = body(postJson("/api/account/passkeys/registration/options", Map.of("password", user.password()),
                session).andExpect(status().isOk()));
        assertThat(options.get("rp").get("id").asText()).isEqualTo("logis.example.org");
        assertThat(options.get("authenticatorSelection").get("residentKey").asText()).isEqualTo("required");
        postJson("/api/account/passkeys/registration", Map.of("name", name, "credential", device.register(options)),
                session).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));
        return device;
    }

    private record Attempt(MockHttpSession session, JsonNode options) {
    }

    private Attempt startSignIn() throws Exception {
        var result = mvc.perform(post("/api/auth/passkey/options").with(csrf())).andExpect(status().isOk()).andReturn();
        var options = json.readTree(result.getResponse().getContentAsString());
        assertThat(options.get("rpId").asText()).isEqualTo("logis.example.org");
        assertThat(options.get("userVerification").asText()).isEqualTo("required");
        return new Attempt((MockHttpSession) result.getRequest().getSession(false), options);
    }

    private org.springframework.test.web.servlet.ResultActions finishSignIn(Attempt attempt, JsonNode credential)
            throws Exception {
        return postJson("/api/auth/passkey", Map.of("credential", credential), attempt.session());
    }

    @Test
    void addListRenameAndDelete() throws Exception {
        var anna = createUser(Role.USER, true);
        var session = login(anna);
        postJson("/api/account/passkeys/registration/options", Map.of("password", "falsch-falsch-falsch"), session)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Das aktuelle Passwort ist falsch."));

        addPasskey(anna, session, "MacBook");
        var list = body(getWith("/api/account/passkeys", session).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("MacBook"))
                .andExpect(jsonPath("$[0].createdAt").exists()));
        var id = list.get(0).get("id").asLong();
        mvc.perform(put("/api/account/passkeys/" + id).with(csrf()).session(session).contentType("application/json")
                .content("{\"name\":\"Altes MacBook\"}")).andExpect(jsonPath("$.name").value("Altes MacBook"));

        // Another user can neither see nor delete it.
        var bert = createUser(Role.USER, true);
        var bertSession = login(bert);
        getWith("/api/account/passkeys", bertSession).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/account/passkeys/" + id).with(csrf()).session(bertSession))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/account/passkeys/" + id).with(csrf()).session(session))
                .andExpect(status().isNoContent());
        getWith("/api/account/passkeys", session).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void notBeforeTwoFactorSetup() throws Exception {
        var dieter = createUser(Role.USER, false);
        var session = passwordStep(dieter);
        postJson("/api/account/passkeys/registration/options", Map.of("password", dieter.password()), session)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signInWithAPasskey() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = addPasskey(anna, login(anna), "MacBook");

        var attempt = startSignIn();
        var credential = device.signIn(attempt.options(), true);
        var result = finishSignIn(attempt, credential).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED")).andReturn();
        var signedIn = (MockHttpSession) result.getRequest().getSession(false);
        getWith("/api/modules", signedIn).andExpect(status().isOk());
        getWith("/api/auth/me", signedIn).andExpect(jsonPath("$.user.username").value(anna.username()))
                .andExpect(jsonPath("$.passkeys").value(true));
        getWith("/api/account/passkeys", signedIn).andExpect(jsonPath("$[0].lastUsedAt").exists());

        // The challenge works once.
        finishSignIn(attempt, credential).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(FAILED));
    }

    @Test
    void startPasswordMustStillBeChanged() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = addPasskey(anna, login(anna), "MacBook");
        var user = users.findById(anna.id()).orElseThrow();
        user.setPasswordChangeRequired(true);
        users.save(user);

        var attempt = startSignIn();
        finishSignIn(attempt, device.signIn(attempt.options(), true))
                .andExpect(jsonPath("$.stage").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void failedAttemptsCountAndUnknownPasskeysFail() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = addPasskey(anna, login(anna), "MacBook");

        var attempt = startSignIn();
        finishSignIn(attempt, device.forgery().signIn(attempt.options(), true)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(FAILED));
        attempt = startSignIn();
        finishSignIn(attempt, device.signIn(attempt.options(), false)).andExpect(status().isUnauthorized());
        assertThat(users.findById(anna.id()).orElseThrow().getFailedAttempts()).isEqualTo(2);

        attempt = startSignIn();
        finishSignIn(attempt, new SoftwareAuthenticator(ORIGIN).forgeryOf(device).signIn(attempt.options(), true))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAndLockedUsersAreRejected() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = addPasskey(anna, login(anna), "MacBook");
        var user = users.findById(anna.id()).orElseThrow();
        user.setEnabled(false);
        users.save(user);
        var attempt = startSignIn();
        finishSignIn(attempt, device.signIn(attempt.options(), true)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(FAILED));

        user = users.findById(anna.id()).orElseThrow();
        user.setEnabled(true);
        user.setLockedUntil(clock.instant().plus(Duration.ofMinutes(5)));
        users.save(user);
        attempt = startSignIn();
        finishSignIn(attempt, device.signIn(attempt.options(), true)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Zu viele Fehlversuche, bitte später erneut versuchen"));
    }

    @Test
    void adminResetRemovesPasskeys() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = addPasskey(anna, login(anna), "Altes iPhone");
        var admin = createUser(Role.ADMIN, true);
        postJson("/api/admin/users/" + anna.id() + "/reset-totp", Map.of(), login(admin)).andExpect(status().isOk());

        var attempt = startSignIn();
        finishSignIn(attempt, device.signIn(attempt.options(), true)).andExpect(status().isUnauthorized());
    }
}
