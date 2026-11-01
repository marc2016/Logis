package app.logis.core.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates the admin from LOGIS_ADMIN_USERNAME / LOGIS_ADMIN_PASSWORD at the very first start.
 * If a user already exists, the configuration is ignored.
 */
@Component
class InitialAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminRunner.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String username;
    private final String password;
    private final ApplicationEventPublisher events;

    InitialAdminRunner(UserRepository users, PasswordEncoder encoder, Clock clock, ApplicationEventPublisher events,
            @Value("${logis.admin.username:}") String username,
            @Value("${logis.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.events = events;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "No user exists yet. Please set LOGIS_ADMIN_USERNAME and LOGIS_ADMIN_PASSWORD.");
        }
        if (!PasswordPolicy.isValid(password)) {
            throw new IllegalStateException(
                    "LOGIS_ADMIN_PASSWORD must be at least " + PasswordPolicy.MIN_LENGTH + " characters long.");
        }
        var admin = new User(username.trim(), username.trim(), Role.ADMIN, encoder.encode(password), clock.instant());
        users.save(admin);
        events.publishEvent(new UserCreatedEvent(admin.getId(), admin.getDisplayName()));
        log.info("Initial admin '{}' created. Two-factor authentication is set up at the first sign-in.", admin.getUsername());
    }
}
