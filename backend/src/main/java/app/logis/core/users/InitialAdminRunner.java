package app.logis.core.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Legt beim allerersten Start den Admin aus LOGIS_ADMIN_USERNAME / LOGIS_ADMIN_PASSWORD an.
 * Existiert bereits ein Benutzer, wird die Konfiguration ignoriert.
 */
@Component
class InitialAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminRunner.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String username;
    private final String password;

    InitialAdminRunner(UserRepository users, PasswordEncoder encoder, Clock clock,
            @Value("${logis.admin.username:}") String username,
            @Value("${logis.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
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
                    "Es existiert noch kein Benutzer. Bitte LOGIS_ADMIN_USERNAME und LOGIS_ADMIN_PASSWORD setzen.");
        }
        if (!PasswordPolicy.isValid(password)) {
            throw new IllegalStateException(
                    "LOGIS_ADMIN_PASSWORD muss mindestens " + PasswordPolicy.MIN_LENGTH + " Zeichen lang sein.");
        }
        var admin = new User(username.trim(), username.trim(), Role.ADMIN, encoder.encode(password), clock.instant());
        users.save(admin);
        log.info("Initialer Admin '{}' angelegt. Zwei-Faktor-Anmeldung wird beim ersten Login eingerichtet.", admin.getUsername());
    }
}
