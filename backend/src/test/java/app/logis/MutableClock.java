package app.logis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Verstellbare Uhr fuer Tests von Sperren, TOTP und Sitzungsdauer. */
public class MutableClock extends Clock {

    private Instant now = Instant.now();

    public void reset() {
        now = Instant.now();
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
