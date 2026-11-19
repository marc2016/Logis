package app.logis.core.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("logis.auth")
/**
 * @param devTotpBypass development only: a correct password completes the sign-in without the second factor
 *                      (see {@link DevTotpBypassGuard})
 */
record AuthProperties(@DefaultValue Session session, @DefaultValue Lockout lockout,
        @DefaultValue RememberMe rememberMe, @DefaultValue("false") boolean devTotpBypass) {

    record Session(@DefaultValue("30m") Duration idleTimeout, @DefaultValue("12h") Duration absoluteTimeout) {
    }

    /**
     * Remembered devices (design.md of remember-me, D5).
     *
     * @param sliding    a device is forgotten this long after its last use
     * @param absolute   a device is forgotten this long after the sign-in that remembered it, however often it is used
     * @param reuseGrace the replaced token is still accepted this long after a rotation (parallel requests)
     */
    record RememberMe(@DefaultValue("30d") Duration sliding, @DefaultValue("90d") Duration absolute,
            @DefaultValue("1m") Duration reuseGrace) {
    }

    record Lockout(@DefaultValue("5") int threshold, @DefaultValue("1m") Duration baseDuration,
            @DefaultValue("15m") Duration maxDuration) {
    }
}
