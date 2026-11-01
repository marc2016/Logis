package app.logis.core.auth;

import app.logis.core.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Counts failed attempts across password and code step and locks with growing duration.
 */
@Component
class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger("auth");

    private final AuthProperties.Lockout lockout;
    private final Clock clock;

    LoginAttemptService(AuthProperties properties, Clock clock) {
        this.lockout = properties.lockout();
        this.clock = clock;
    }

    boolean isLocked(User user) {
        return user.isLockedAt(clock.instant());
    }

    void recordFailure(User user, String ip, String stage) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= lockout.threshold()) {
            user.setLockedUntil(clock.instant().plus(lockDuration(attempts)));
        }
        logFailure(user.getUsername(), ip, stage);
    }

    void recordSuccess(User user) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
    }

    void logFailure(String username, String ip, String stage) {
        log.warn("auth.failed user={} ip={} stage={}", sanitize(username), ip, stage);
    }

    Duration lockDuration(int attempts) {
        int exponent = Math.min(attempts - lockout.threshold(), 20);
        var duration = lockout.baseDuration().multipliedBy(1L << exponent);
        return duration.compareTo(lockout.maxDuration()) > 0 ? lockout.maxDuration() : duration;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        var clean = value.replaceAll("[\\p{Cntrl}]", "_");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }
}
