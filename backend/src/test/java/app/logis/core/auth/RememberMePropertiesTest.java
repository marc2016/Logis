package app.logis.core.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RememberMePropertiesTest {

    private static AuthProperties.RememberMe bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("logis.auth", AuthProperties.class)
                .rememberMe();
    }

    @Test
    void defaults() {
        var settings = bind(Map.of());
        assertThat(settings.sliding()).isEqualTo(Duration.ofDays(30));
        assertThat(settings.absolute()).isEqualTo(Duration.ofDays(90));
        assertThat(settings.reuseGrace()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void overrides() {
        var settings = bind(Map.of("logis.auth.remember-me.sliding", "14d", "logis.auth.remember-me.absolute", "30d",
                "logis.auth.remember-me.reuse-grace", "30s"));
        assertThat(settings.sliding()).isEqualTo(Duration.ofDays(14));
        assertThat(settings.absolute()).isEqualTo(Duration.ofDays(30));
        assertThat(settings.reuseGrace()).isEqualTo(Duration.ofSeconds(30));
    }
}
