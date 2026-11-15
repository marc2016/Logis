package app.logis.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * {@code logis.auth.dev-totp-bypass} turns off the second sign-in factor. It is only allowed with the {@code dev}
 * profile: anywhere else the application refuses to start (design.md D3 of dev-totp-bypass).
 */
@Component
class DevTotpBypassGuard {

    private static final Logger log = LoggerFactory.getLogger(DevTotpBypassGuard.class);
    static final String ONLY_IN_DEV = "logis.auth.dev-totp-bypass is only allowed with the dev profile";

    DevTotpBypassGuard(AuthProperties properties, Environment environment) {
        check(properties.devTotpBypass(), environment);
    }

    static void check(boolean bypass, Environment environment) {
        if (!bypass) {
            return;
        }
        if (!environment.matchesProfiles("dev")) {
            throw new IllegalStateException(ONLY_IN_DEV);
        }
        log.warn("Two-factor sign-in is disabled (dev)");
    }
}
