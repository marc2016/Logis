package app.logis.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Logis runs on a UTC clock, but its users and their days are German: between midnight and 1 or 2 a.m. German time,
 * UTC is still on the previous day. Every calendar date ("today", the day a mail arrived) is taken in German time.
 */
public final class GermanTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private GermanTime() {
    }

    /** Today in Germany. */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    /** The German calendar date of an instant. */
    public static LocalDate date(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }
}
