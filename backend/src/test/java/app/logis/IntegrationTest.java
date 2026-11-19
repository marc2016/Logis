package app.logis;

import app.logis.core.users.Role;
import app.logis.core.users.User;
import app.logis.core.users.UserCreatedEvent;
import app.logis.core.users.UserRepository;
import app.logis.shared.SecretCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::url);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected SecretCipher cipher;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected org.springframework.context.ApplicationEventPublisher events;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    public record TestUser(Long id, String username, String password, String totpSecret) {
    }

    protected TestUser createUser(Role role, boolean withTotp) {
        return createUser(role, withTotp, false);
    }

    protected TestUser createUser(Role role, boolean withTotp, boolean passwordChangeRequired) {
        var username = "user" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000;
        var password = "password-" + username;
        var user = new User(username, "Test " + username, role, encoder.encode(password), clock.instant());
        user.setPasswordChangeRequired(passwordChangeRequired);
        String secret = null;
        if (withTotp) {
            secret = new DefaultSecretGenerator(32).generate();
            user.setTotpSecretEnc(cipher.encrypt(secret));
        }
        users.save(user);
        // As in the application: the private legal entity is created through the event.
        events.publishEvent(new UserCreatedEvent(user.getId(), user.getDisplayName()));
        return new TestUser(user.getId(), username, password, secret);
    }

    /** TOTP code for the current time step of the test clock. */
    protected String totp(String secret) {
        try {
            long step = clock.instant().getEpochSecond() / 30;
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, step);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected ResultActions postJson(String url, Object body, MockHttpSession session) throws Exception {
        var request = post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request);
    }

    protected MockHttpSession passwordStep(TestUser user) throws Exception {
        var result = postJson("/api/auth/login", Map.of("username", user.username(), "password", user.password()), null)
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Full sign-in (password + TOTP). Advances the clock so that every code is fresh. */
    protected MockHttpSession login(TestUser user) throws Exception {
        clock.advance(Duration.ofSeconds(31));
        var session = passwordStep(user);
        postJson("/api/auth/totp", Map.of("code", totp(user.totpSecret())), session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED"));
        return session;
    }

    protected ResultActions getWith(String url, MockHttpSession session) throws Exception {
        return mvc.perform(get(url).session(session));
    }

    // --- Remembered devices („Angemeldet bleiben“) ------------------------------------------------

    protected static final String REMEMBER_COOKIE = "LOGIS_REMEMBER";
    protected static final String FIREFOX_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0";

    /**
     * A browser with a device cookie: sends the cookie and its user agent, follows cookie changes and keeps the
     * session it gets, like a real browser. {@link #loseSession()} simulates an expired session or a restart.
     */
    protected class Device {

        public final String userAgent;
        public String cookie;
        /** The long-lived device cookie of sign-in warnings (LOGIS_DEVICE). */
        public String deviceCookie;
        /** Client address of the requests; null: MockMvc's default. */
        public String ip;
        public MockHttpSession session;

        public Device(String userAgent) {
            this.userAgent = userAgent;
        }

        public void loseSession() {
            session = null;
        }

        public org.springframework.test.web.servlet.MvcResult get(String url) throws Exception {
            return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
        }

        public org.springframework.test.web.servlet.MvcResult post(String url, Object body) throws Exception {
            return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
        }

        public org.springframework.test.web.servlet.MvcResult delete(String url) throws Exception {
            return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url).with(csrf()));
        }

        private org.springframework.test.web.servlet.MvcResult perform(
                org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
            request.header("User-Agent", userAgent);
            var cookies = new java.util.ArrayList<jakarta.servlet.http.Cookie>();
            if (cookie != null) {
                cookies.add(new jakarta.servlet.http.Cookie(REMEMBER_COOKIE, cookie));
            }
            if (deviceCookie != null) {
                cookies.add(new jakarta.servlet.http.Cookie(DEVICE_COOKIE, deviceCookie));
            }
            if (!cookies.isEmpty()) {
                request.cookie(cookies.toArray(jakarta.servlet.http.Cookie[]::new));
            }
            if (ip != null) {
                var address = ip;
                request.with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                });
            }
            if (session != null) {
                request.session(session);
            }
            var result = mvc.perform(request).andReturn();
            var set = rememberCookie(result);
            if (set != null) {
                cookie = set.isEmpty() ? null : set;
            }
            var device = cookieValue(result, DEVICE_COOKIE);
            if (device != null) {
                deviceCookie = device.isEmpty() ? null : device;
            }
            var current = (MockHttpSession) result.getRequest().getSession(false);
            session = current == null || current.isInvalid() ? null : current;
            return result;
        }

        /** Full sign-in with password and code; {@code remember} checks „Angemeldet bleiben“. */
        public Device signIn(TestUser user, boolean remember) throws Exception {
            clock.advance(Duration.ofSeconds(31));
            session = null;
            var body = new java.util.HashMap<String, Object>(Map.of("username", user.username(),
                    "password", user.password()));
            body.put("rememberMe", remember);
            assertStatus(post("/api/auth/login", body), 200);
            var result = post("/api/auth/totp", Map.of("code", totp(user.totpSecret())));
            assertStatus(result, 200);
            return this;
        }
    }

    protected static final String DEVICE_COOKIE = "LOGIS_DEVICE";

    /** Value of the remember cookie set by the response; "" if it was cleared, null if not touched. */
    protected static String rememberCookie(org.springframework.test.web.servlet.MvcResult result) {
        return cookieValue(result, REMEMBER_COOKIE);
    }

    protected static String cookieValue(org.springframework.test.web.servlet.MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.substring(name.length() + 1, h.indexOf(';')))
                .reduce((a, b) -> b).orElse(null);
    }

    protected static void assertStatus(org.springframework.test.web.servlet.MvcResult result, int expected)
            throws Exception {
        if (result.getResponse().getStatus() != expected) {
            throw new AssertionError("Expected status " + expected + " but was " + result.getResponse().getStatus()
                    + " for " + result.getRequest().getMethod() + " " + result.getRequest().getRequestURI() + ": "
                    + result.getResponse().getContentAsString());
        }
    }
}
