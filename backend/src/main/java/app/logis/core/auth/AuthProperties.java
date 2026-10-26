package app.logis.core.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("logis.auth")
record AuthProperties(@DefaultValue Session session, @DefaultValue Lockout lockout) {

    record Session(@DefaultValue("30m") Duration idleTimeout, @DefaultValue("12h") Duration absoluteTimeout) {
    }

    record Lockout(@DefaultValue("5") int threshold, @DefaultValue("1m") Duration baseDuration,
            @DefaultValue("15m") Duration maxDuration) {
    }
}
