package app.logis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Kein generierter Standardbenutzer: Die Anmeldung laeuft ausschliesslich ueber core.auth.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LogisApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisApplication.class, args);
    }
}
