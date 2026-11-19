package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** „Angemeldet bleiben“ end to end through the API (specs/authentication of remember-me). */
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class RememberMeTest extends IntegrationTest {

    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final String ORIGIN = "https://logis.example.org";

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private long devicesOf(TestUser user) {
        return jdbc.queryForObject("SELECT count(*) FROM core.remembered_devices WHERE user_id = ?", Long.class,
                user.id());
    }

    // --- 3.1 Issue ---------------------------------------------------------------------------------

    @Test
    void cookieOnlyWhenRequested() throws Exception {
        var anna = createUser(Role.USER, true);
        assertThat(new Device(FIREFOX_LINUX).signIn(anna, false).cookie).isNull();
        assertThat(new Device(FIREFOX_LINUX).signIn(anna, true).cookie).isNotNull();
        assertThat(devicesOf(anna)).isEqualTo(1);
    }

    @Test
    void passkeySignInRemembersTheDevice() throws Exception {
        var anna = createUser(Role.USER, true);
        var browser = new Device(FIREFOX_LINUX).signIn(anna, false);
        var authenticator = new SoftwareAuthenticator(ORIGIN);
        var options = body(browser.post("/api/account/passkeys/registration/options",
                Map.of("password", anna.password())));
        assertStatus(browser.post("/api/account/passkeys/registration",
                Map.of("name", "Laptop", "credential", authenticator.register(options))), 200);

        var device = new Device(FIREFOX_LINUX);
        var signInOptions = body(device.post("/api/auth/passkey/options", Map.of()));
        var result = device.post("/api/auth/passkey",
                Map.of("credential", authenticator.signIn(signInOptions, true), "rememberMe", true));
        assertStatus(result, 200);
        assertThat(device.cookie).isNotNull();

        device.loseSession();
        assertStatus(device.get("/api/modules"), 200);
    }

    @Test
    void notRememberedBeforeTheStartPasswordIsChanged() throws Exception {
        var newUser = createUser(Role.USER, true, true);
        var device = new Device(FIREFOX_LINUX).signIn(newUser, true);
        assertThat(body(device.get("/api/auth/me")).get("stage").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(device.cookie).isNull();

        assertStatus(device.post("/api/auth/password", Map.of("newPassword", "ein-neues-passwort-1")), 200);
        assertThat(device.cookie).isNotNull();
    }

    @Test
    void newSignInReplacesTheDeviceCookie() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        var first = device.cookie;
        device.signIn(anna, true);
        assertThat(device.cookie).isNotEqualTo(first);
        assertThat(devicesOf(anna)).isEqualTo(1);

        device.signIn(anna, false);
        assertThat(device.cookie).isNull();
        assertThat(devicesOf(anna)).isZero();
    }

    // --- 3.2 Restore -------------------------------------------------------------------------------

    @Test
    void restoresAfterTheSessionIsGone() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);

        device.loseSession();
        var me = device.get("/api/auth/me");
        assertThat(body(me).get("stage").asText()).isEqualTo("AUTHENTICATED");
        assertThat(body(me).get("user").get("username").asText()).isEqualTo(anna.username());

        // Idle timeout of the new session: the device starts another one.
        clock.advance(Duration.ofHours(2));
        assertStatus(device.get("/api/modules"), 200);

        // Without the device cookie: signed out as before.
        clock.advance(Duration.ofHours(2));
        device.cookie = null;
        assertStatus(device.get("/api/modules"), 401);
    }

    @Test
    void parallelRequestsWithTheSameCookie() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        var cookie = device.cookie;

        var first = new Device(FIREFOX_LINUX);
        first.cookie = cookie;
        var second = new Device(FIREFOX_LINUX);
        second.cookie = cookie;
        assertStatus(first.get("/api/modules"), 200);
        assertStatus(second.get("/api/modules"), 200);
    }

    @Test
    void replayedCookieEndsTheRealDeviceToo() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        var copy = device.cookie;

        device.loseSession();
        assertStatus(device.get("/api/modules"), 200);

        clock.advance(Duration.ofMinutes(2));
        var thief = new Device(FIREFOX_LINUX);
        thief.cookie = copy;
        assertStatus(thief.get("/api/modules"), 401);

        device.loseSession();
        assertStatus(device.get("/api/modules"), 401);
    }

    @Test
    void otherBrowserIsRejected() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        var thief = new Device(CHROME_ANDROID);
        thief.cookie = device.cookie;
        assertStatus(thief.get("/api/modules"), 401);
        assertThat(devicesOf(anna)).isZero();
    }

    @Test
    void signInStepsDoNotRestore() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        device.loseSession();
        var result = device.post("/api/auth/totp", Map.of("code", "123456"));
        assertStatus(result, 401);
        assertThat(device.session == null || device.session.getAttribute(AuthSession.USER_ID) == null).isTrue();
    }

    @Test
    void lastUseIsKeptCurrentWhileTheSessionIsActive() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        // An active session never has to restore, but its device's last use still moves forward.
        for (int i = 0; i < 9; i++) {
            clock.advance(Duration.ofMinutes(20));
            assertStatus(device.get("/api/modules"), 200);
        }
        var lastUsed = jdbc.queryForObject("SELECT last_used_at FROM core.remembered_devices WHERE user_id = ?",
                java.sql.Timestamp.class, anna.id()).toInstant();
        assertThat(Duration.between(lastUsed, clock.instant())).isLessThanOrEqualTo(Duration.ofHours(1));
    }

    // --- 3.3 Sign out ------------------------------------------------------------------------------

    @Test
    void signOutForgetsTheDevice() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        var cookie = device.cookie;
        assertStatus(device.post("/api/auth/logout", Map.of()), 204);
        assertThat(device.cookie).isNull();
        assertThat(devicesOf(anna)).isZero();

        var copy = new Device(FIREFOX_LINUX);
        copy.cookie = cookie;
        assertStatus(copy.get("/api/modules"), 401);
    }

    // --- 3.4 Own password change -------------------------------------------------------------------

    @Test
    void passwordChangeKeepsOnlyTheCurrentDevice() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = new Device(FIREFOX_LINUX).signIn(anna, true);
        var phone = new Device(CHROME_ANDROID).signIn(anna, true);

        assertStatus(laptop.post("/api/account/password",
                Map.of("currentPassword", anna.password(), "newPassword", "ein-neues-passwort-1")), 200);

        phone.loseSession();
        assertStatus(phone.get("/api/modules"), 401);
        assertStatus(laptop.get("/api/modules"), 200);
        laptop.loseSession();
        assertStatus(laptop.get("/api/modules"), 200);
    }

    // --- 4.1 Admin area ----------------------------------------------------------------------------

    @Test
    void restoredAdminNeedsAFreshSignIn() throws Exception {
        var admin = createUser(Role.ADMIN, true);
        var device = new Device(FIREFOX_LINUX).signIn(admin, true);
        assertStatus(device.get("/api/admin/users"), 200);
        assertThat(body(device.get("/api/auth/me")).get("reauthenticationRequired").asBoolean()).isFalse();

        device.loseSession();
        assertThat(body(device.get("/api/auth/me")).get("reauthenticationRequired").asBoolean()).isTrue();
        var denied = device.get("/api/admin/users");
        assertStatus(denied, 403);
        assertThat(body(denied).get("type").asText()).isEqualTo("urn:logis:reauthentication-required");
        assertThat(body(denied).get("detail").asText()).isEqualTo("Für den Admin-Bereich bitte erneut anmelden");
        assertStatus(device.get("/api/modules"), 200);

        assertStatus(device.post("/api/auth/reauthenticate", Map.of()), 204);
        assertThat(body(device.get("/api/auth/me")).get("stage").asText()).isEqualTo("ANONYMOUS");
        assertThat(device.cookie).isNotNull();

        device.signIn(admin, true);
        assertStatus(device.get("/api/admin/users"), 200);
        assertThat(devicesOf(admin)).isEqualTo(1);
    }

    @Test
    void ordinaryUsersStillGetAPlainForbidden() throws Exception {
        var anna = createUser(Role.USER, true);
        var device = new Device(FIREFOX_LINUX).signIn(anna, true);
        device.loseSession();
        var denied = device.get("/api/admin/users");
        assertStatus(denied, 403);
        assertThat(denied.getResponse().getContentAsString()).doesNotContain("reauthentication");
    }

    // --- 4.2 Device list ---------------------------------------------------------------------------

    @Test
    void listAndSignOutDevices() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = new Device(FIREFOX_LINUX).signIn(anna, true);
        var phone = new Device(CHROME_ANDROID).signIn(anna, true);
        var other = new Device(FIREFOX_LINUX).signIn(createUser(Role.USER, true), true);

        var list = body(laptop.get("/api/account/devices"));
        assertThat(list).hasSize(2);
        JsonNode phoneEntry = null;
        for (var entry : list) {
            if (entry.get("current").asBoolean()) {
                assertThat(entry.get("browser").asText()).isEqualTo("Firefox");
                assertThat(entry.get("os").asText()).isEqualTo("Linux");
            } else {
                phoneEntry = entry;
            }
        }
        assertThat(phoneEntry).isNotNull();
        assertThat(phoneEntry.get("browser").asText()).isEqualTo("Chrome");
        assertThat(phoneEntry.get("os").asText()).isEqualTo("Android");
        assertThat(phoneEntry.get("createdAt").isTextual()).isTrue();
        assertThat(phoneEntry.get("lastUsedAt").isTextual()).isTrue();

        // The current device is also marked after a restore.
        laptop.loseSession();
        assertThat(body(laptop.get("/api/account/devices")).findValuesAsText("current")).contains("true");

        assertStatus(laptop.delete("/api/account/devices/" + phoneEntry.get("id").asLong()), 204);
        assertThat(body(laptop.get("/api/account/devices"))).hasSize(1);
        phone.loseSession();
        assertStatus(phone.get("/api/modules"), 401);

        var foreignId = body(other.get("/api/account/devices")).get(0).get("id").asLong();
        assertStatus(laptop.delete("/api/account/devices/" + foreignId), 404);
    }

    @Test
    void signOutAllOtherDevices() throws Exception {
        var anna = createUser(Role.USER, true);
        var laptop = new Device(FIREFOX_LINUX).signIn(anna, true);
        var phone = new Device(CHROME_ANDROID).signIn(anna, true);
        var tablet = new Device(FIREFOX_LINUX).signIn(anna, false);

        assertStatus(laptop.post("/api/account/devices/sign-out-others", Map.of()), 204);

        assertStatus(tablet.get("/api/modules"), 401);
        assertStatus(phone.get("/api/modules"), 401);
        phone.loseSession();
        assertStatus(phone.get("/api/modules"), 401);
        assertStatus(laptop.get("/api/modules"), 200);
        laptop.loseSession();
        assertStatus(laptop.get("/api/modules"), 200);
        assertThat(body(laptop.get("/api/account/devices"))).hasSize(1);
    }
}
