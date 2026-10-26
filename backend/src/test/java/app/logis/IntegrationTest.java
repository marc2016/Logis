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
        registry.add("spring.datasource.url", TestPostgres.CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", TestPostgres.CONTAINER::getUsername);
        registry.add("spring.datasource.password", TestPostgres.CONTAINER::getPassword);
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
        // Wie in der Anwendung: privater Rechtstraeger entsteht ueber das Event.
        events.publishEvent(new UserCreatedEvent(user.getId(), user.getDisplayName()));
        return new TestUser(user.getId(), username, password, secret);
    }

    /** TOTP-Code fuer den aktuellen Zeitschritt der Test-Uhr. */
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

    /** Vollstaendige Anmeldung (Passwort + TOTP). Rueckt die Uhr vor, damit jeder Code frisch ist. */
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
}
