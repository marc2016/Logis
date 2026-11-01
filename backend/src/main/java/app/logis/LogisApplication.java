package app.logis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// No generated default user: sign-in runs exclusively through core.auth.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LogisApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisApplication.class, args);
    }
}
