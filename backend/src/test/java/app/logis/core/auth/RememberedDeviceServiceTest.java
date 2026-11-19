package app.logis.core.auth;

import app.logis.IntegrationTest;
import app.logis.core.users.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Issue, restore, rotation, reuse and durations of remembered devices (design D2–D6 of remember-me). */
class RememberedDeviceServiceTest extends IntegrationTest {

    static final String FIREFOX_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0";
    static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    @Autowired
    RememberedDeviceService devices;
    @Autowired
    JdbcTemplate jdbc;

    private MockHttpServletRequest request(String cookie, String userAgent) {
        var request = new MockHttpServletRequest("GET", "/api/modules");
        request.addHeader(HttpHeaders.USER_AGENT, userAgent);
        if (cookie != null) {
            request.setCookies(new Cookie(RememberedDeviceService.COOKIE, cookie));
        }
        return request;
    }

    /** Value of the device cookie set by the response; "" if it was cleared, null if not touched. */
    private static String cookie(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(RememberedDeviceService.COOKIE + "="))
                .map(h -> h.substring(RememberedDeviceService.COOKIE.length() + 1, h.indexOf(';')))
                .reduce((a, b) -> b).orElse(null);
    }

    private record Issued(String cookie, Long userId) {
    }

    private Issued issue(String userAgent) {
        var anna = createUser(Role.USER, true);
        var user = users.findById(anna.id()).orElseThrow();
        var request = request(null, userAgent);
        request.getSession(true).setAttribute(AuthSession.REMEMBER_REQUESTED, true);
        var response = new MockHttpServletResponse();
        devices.onAuthenticated(request, response, user);
        var value = cookie(response);
        assertThat(value).isNotBlank();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "SameSite=Lax", "Path=/api",
                "Max-Age=" + Duration.ofDays(90).toSeconds());
        return new Issued(value, anna.id());
    }

    private record Result(boolean restored, String cookie) {
    }

    private Result restore(String cookie, String userAgent) {
        var response = new MockHttpServletResponse();
        var restored = devices.restore(request(cookie, userAgent), response);
        return new Result(restored.isPresent(), cookie(response));
    }

    private long count(Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM core.remembered_devices WHERE user_id = ?", Long.class, userId);
    }

    @Test
    void notRememberedWithoutTheRequest() {
        var anna = createUser(Role.USER, true);
        var request = request(null, FIREFOX_WINDOWS);
        request.getSession(true);
        var response = new MockHttpServletResponse();
        devices.onAuthenticated(request, response, users.findById(anna.id()).orElseThrow());
        assertThat(cookie(response)).isNull();
        assertThat(count(anna.id())).isZero();
    }

    @Test
    void restoreRotatesTheToken() {
        var issued = issue(FIREFOX_WINDOWS);
        var first = restore(issued.cookie(), FIREFOX_WINDOWS);
        assertThat(first.restored()).isTrue();
        assertThat(first.cookie()).isNotBlank().isNotEqualTo(issued.cookie());
        // Same series, new token.
        assertThat(first.cookie().split(":")[0]).isEqualTo(issued.cookie().split(":")[0]);

        var second = restore(first.cookie(), FIREFOX_WINDOWS);
        assertThat(second.restored()).isTrue();
        assertThat(second.cookie()).isNotEqualTo(first.cookie());
    }

    @Test
    void previousTokenWithinTheGraceWindow() {
        var issued = issue(FIREFOX_WINDOWS);
        var rotated = restore(issued.cookie(), FIREFOX_WINDOWS);

        clock.advance(Duration.ofSeconds(30));
        var parallel = restore(issued.cookie(), FIREFOX_WINDOWS);
        assertThat(parallel.restored()).isTrue();
        assertThat(parallel.cookie()).isNull();
        assertThat(restore(rotated.cookie(), FIREFOX_WINDOWS).restored()).isTrue();
    }

    @Test
    void previousTokenAfterTheGraceWindowRevokesTheSeries() {
        var issued = issue(FIREFOX_WINDOWS);
        var rotated = restore(issued.cookie(), FIREFOX_WINDOWS);

        clock.advance(Duration.ofSeconds(61));
        var replay = restore(issued.cookie(), FIREFOX_WINDOWS);
        assertThat(replay.restored()).isFalse();
        assertThat(replay.cookie()).isEmpty();
        assertThat(count(issued.userId())).isZero();
        // The real device has to sign in again too.
        assertThat(restore(rotated.cookie(), FIREFOX_WINDOWS).restored()).isFalse();
    }

    @Test
    void otherBrowserRevokesTheSeries() {
        var issued = issue(FIREFOX_WINDOWS);
        var other = restore(issued.cookie(), CHROME_ANDROID);
        assertThat(other.restored()).isFalse();
        assertThat(other.cookie()).isEmpty();
        assertThat(restore(issued.cookie(), FIREFOX_WINDOWS).restored()).isFalse();
    }

    @Test
    void forgottenAfter30DaysWithoutUse() {
        var issued = issue(FIREFOX_WINDOWS);
        clock.advance(Duration.ofDays(30).plusMinutes(1));
        assertThat(restore(issued.cookie(), FIREFOX_WINDOWS).restored()).isFalse();
        assertThat(count(issued.userId())).isZero();
    }

    @Test
    void forgottenAfter90DaysEvenWithRegularUse() {
        var cookie = issue(FIREFOX_WINDOWS).cookie();
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofDays(20));
            var result = restore(cookie, FIREFOX_WINDOWS);
            assertThat(result.restored()).isTrue();
            cookie = result.cookie();
        }
        clock.advance(Duration.ofDays(11));
        assertThat(restore(cookie, FIREFOX_WINDOWS).restored()).isFalse();
    }

    @Test
    void disabledUserAndChangedSessionVersion() {
        var disabled = issue(FIREFOX_WINDOWS);
        var user = users.findById(disabled.userId()).orElseThrow();
        user.setEnabled(false);
        users.save(user);
        assertThat(restore(disabled.cookie(), FIREFOX_WINDOWS).restored()).isFalse();

        var revoked = issue(FIREFOX_WINDOWS);
        var other = users.findById(revoked.userId()).orElseThrow();
        other.invalidateSessions();
        users.save(other);
        assertThat(restore(revoked.cookie(), FIREFOX_WINDOWS).restored()).isFalse();
    }

    @Test
    void onlyHashesAreStored() {
        var issued = issue(FIREFOX_WINDOWS);
        var token = java.util.Base64.getUrlDecoder().decode(issued.cookie().split(":")[1]);
        var stored = jdbc.queryForObject("SELECT token_hash FROM core.remembered_devices WHERE user_id = ?",
                byte[].class, issued.userId());
        assertThat(Arrays.equals(stored, token)).isFalse();
        assertThat(stored).isEqualTo(RememberedDeviceService.hash(token));
    }

    @Test
    void unknownOrMalformedCookies() {
        assertThat(restore("not-a-cookie", FIREFOX_WINDOWS)).isEqualTo(new Result(false, null));
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        var unknown = encoder.encodeToString(new byte[32]) + ":" + encoder.encodeToString(new byte[32]);
        assertThat(restore(unknown, FIREFOX_WINDOWS)).isEqualTo(new Result(false, ""));
    }

    @Test
    void cleanUpRemovesStaleDevices() {
        var fresh = issue(FIREFOX_WINDOWS);
        var unused = issue(FIREFOX_WINDOWS);
        var revoked = issue(FIREFOX_WINDOWS);
        jdbc.update("UPDATE core.remembered_devices SET last_used_at = last_used_at - interval '31 days' "
                + "WHERE user_id = ?", unused.userId());
        jdbc.update("UPDATE core.users SET session_version = session_version + 1 WHERE id = ?", revoked.userId());

        devices.deleteStale();

        assertThat(count(fresh.userId())).isEqualTo(1);
        assertThat(count(unused.userId())).isZero();
        assertThat(count(revoked.userId())).isZero();
    }
}
