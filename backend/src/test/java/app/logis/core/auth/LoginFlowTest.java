package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class LoginFlowTest extends IntegrationTest {

    private static final String INVALID_CREDENTIALS = "Benutzername oder Passwort ist falsch";

    // --- 5.1 Password step ---------------------------------------------------------------------

    @Test
    void passwordStepLeadsToTotpButGrantsNoAccess() throws Exception {
        var user = createUser(Role.USER, true);
        var session = passwordStep(user);

        getWith("/api/auth/me", session).andExpect(jsonPath("$.stage").value("TOTP_REQUIRED"));
        getWith("/api/modules", session).andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutTotpMustEnroll() throws Exception {
        var user = createUser(Role.USER, false);
        var session = passwordStep(user);

        getWith("/api/auth/me", session).andExpect(jsonPath("$.stage").value("TOTP_ENROLLMENT_REQUIRED"));
        getWith("/api/modules", session).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordUnknownAndDisabledUserLookIdentical() throws Exception {
        var user = createUser(Role.USER, true);
        var disabled = createUser(Role.USER, true);
        var entity = users.findById(disabled.id()).orElseThrow();
        entity.setEnabled(false);
        users.save(entity);

        for (var creds : List.of(
                Map.of("username", user.username(), "password", "falsches-passwort"),
                Map.of("username", "does-not-exist", "password", "whatever-whatever"),
                Map.of("username", disabled.username(), "password", disabled.password()))) {
            postJson("/api/auth/login", creds, null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
        }
    }

    @Test
    void sessionIdChangesOnEachStage() throws Exception {
        var user = createUser(Role.USER, true);
        var session = passwordStep(user);
        var idAfterPassword = session.getId();
        postJson("/api/auth/totp", Map.of("code", totp(user.totpSecret())), session).andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(idAfterPassword);
    }

    // --- 5.2 Setup ----------------------------------------------------------------------------

    @Test
    void enrollmentWithValidCode() throws Exception {
        var user = createUser(Role.USER, false);
        var session = passwordStep(user);

        var setup = postJson("/api/auth/totp/setup", Map.of(), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri", startsWith("otpauth://totp/Logis:")))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.secret");

        postJson("/api/auth/totp/confirm", Map.of("code", totp(secret)), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED"))
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)));

        var stored = users.findById(user.id()).orElseThrow().getTotpSecretEnc();
        assertThat(stored).isNotNull().doesNotContain(secret);
        assertThat(cipher.decrypt(stored)).isEqualTo(secret);
        getWith("/api/modules", session).andExpect(status().isOk());
    }

    @Test
    void enrollmentWithWrongCodeStoresNothing() throws Exception {
        var user = createUser(Role.USER, false);
        var session = passwordStep(user);
        postJson("/api/auth/totp/setup", Map.of(), session).andExpect(status().isOk());

        postJson("/api/auth/totp/confirm", Map.of("code", "000000"), session)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Code ist ungültig"));

        assertThat(users.findById(user.id()).orElseThrow().isTotpEnabled()).isFalse();
        getWith("/api/auth/me", session).andExpect(jsonPath("$.stage").value("TOTP_ENROLLMENT_REQUIRED"));
    }

    // --- 5.3 TOTP / recovery ------------------------------------------------------------------

    @Test
    void reusedTotpCodeIsRejected() throws Exception {
        var user = createUser(Role.USER, true);
        var code = totp(user.totpSecret());
        var first = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", code), first).andExpect(status().isOk());

        var second = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", code), second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Code ist ungültig"));
    }

    @Test
    void wrongTotpCodeAllowsRetry() throws Exception {
        var user = createUser(Role.USER, true);
        var session = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", "123456"), session).andExpect(status().isUnauthorized());
        postJson("/api/auth/totp", Map.of("code", totp(user.totpSecret())), session)
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED"));
    }

    @Test
    void recoveryCodeWorksExactlyOnce() throws Exception {
        var user = createUser(Role.USER, true);
        var codes = regenerateCodes(login(user));
        var recovery = codes.get(0);

        var session = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", recovery.toLowerCase()), session)
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED"));

        var again = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", recovery), again).andExpect(status().isUnauthorized());
    }

    // --- 5.4 Forced password change -----------------------------------------------------------

    @Test
    void passwordChangeRequiredBeforeAccess() throws Exception {
        var user = createUser(Role.USER, true, true);
        var session = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", totp(user.totpSecret())), session)
                .andExpect(jsonPath("$.stage").value("PASSWORD_CHANGE_REQUIRED"));
        getWith("/api/modules", session).andExpect(status().isUnauthorized());

        postJson("/api/auth/password", Map.of("newPassword", "kurz"), session).andExpect(status().isBadRequest());
        postJson("/api/auth/password", Map.of("newPassword", user.password()), session).andExpect(status().isBadRequest());
        postJson("/api/auth/password", Map.of("newPassword", "mein-neues-passwort"), session)
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED"));
        getWith("/api/modules", session).andExpect(status().isOk());

        assertThat(encoder.matches("mein-neues-passwort",
                users.findById(user.id()).orElseThrow().getPasswordHash())).isTrue();
    }

    // --- 5.5 Lockout --------------------------------------------------------------------------

    @Test
    void lockoutAfterFiveFailuresAndRecovery(CapturedOutput output) throws Exception {
        var user = createUser(Role.USER, true);
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login", Map.of("username", user.username(), "password", "falsch-" + i), null)
                    .andExpect(status().isUnauthorized());
        }
        postJson("/api/auth/login", Map.of("username", user.username(), "password", user.password()), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Zu viele Fehlversuche, bitte später erneut versuchen"));

        clock.advance(Duration.ofSeconds(61));
        login(user);
        assertThat(users.findById(user.id()).orElseThrow().getFailedAttempts()).isZero();

        assertThat(output).contains("auth.failed user=" + user.username());
        assertThat(output).doesNotContain("falsch-0").doesNotContain(user.password());
    }

    @Test
    void codeFailuresCountTowardsLockout() throws Exception {
        var user = createUser(Role.USER, true);
        var session = passwordStep(user);
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/totp", Map.of("code", "00000" + i), session).andExpect(status().isUnauthorized());
        }
        postJson("/api/auth/totp", Map.of("code", totp(user.totpSecret())), session)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void lockDurationGrowsUpToFifteenMinutes() throws Exception {
        var user = createUser(Role.USER, true);
        var entity = users.findById(user.id()).orElseThrow();
        entity.setFailedAttempts(30);
        users.save(entity);
        postJson("/api/auth/login", Map.of("username", user.username(), "password", "falsch-falsch"), null)
                .andExpect(status().isUnauthorized());
        var lockedUntil = users.findById(user.id()).orElseThrow().getLockedUntil();
        assertThat(Duration.between(clock.instant(), lockedUntil)).isEqualTo(Duration.ofMinutes(15));
    }

    // --- 5.6 Session duration -----------------------------------------------------------------

    @Test
    void idleSessionExpiresAfterThirtyMinutes() throws Exception {
        var session = login(createUser(Role.USER, true));
        clock.advance(Duration.ofMinutes(29));
        getWith("/api/modules", session).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(31));
        getWith("/api/modules", session).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionEndsAfterTwelveHoursDespiteActivity() throws Exception {
        var session = login(createUser(Role.USER, true));
        for (int i = 0; i < 36; i++) {
            clock.advance(Duration.ofMinutes(20));
            getWith("/api/modules", session).andExpect(status().isOk());
        }
        clock.advance(Duration.ofMinutes(20));
        getWith("/api/modules", session).andExpect(status().isUnauthorized());
    }

    // --- 5.7 Sign-out -------------------------------------------------------------------------

    @Test
    void logoutEndsSession() throws Exception {
        var session = login(createUser(Role.USER, true));
        var cookieHolder = new MockHttpSession(null, session.getId());
        postJson("/api/auth/logout", Map.of(), session).andExpect(status().isNoContent());
        getWith("/api/modules", session).andExpect(status().isUnauthorized());
        getWith("/api/modules", cookieHolder).andExpect(status().isUnauthorized());
    }

    private List<String> regenerateCodes(MockHttpSession session) throws Exception {
        var body = postJson("/api/account/recovery-codes", Map.of(), session)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.recoveryCodes");
    }
}
