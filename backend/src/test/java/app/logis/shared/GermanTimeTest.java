package app.logis.shared;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GermanTimeTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void todayIsTheGermanDay() {
        // Winter: one hour ahead of UTC.
        assertThat(GermanTime.today(at("2027-01-14T22:59:00Z"))).isEqualTo(LocalDate.of(2027, 1, 14));
        assertThat(GermanTime.today(at("2027-01-14T23:00:00Z"))).isEqualTo(LocalDate.of(2027, 1, 15));
        // Summer: two hours ahead.
        assertThat(GermanTime.today(at("2027-07-14T21:59:00Z"))).isEqualTo(LocalDate.of(2027, 7, 14));
        assertThat(GermanTime.today(at("2027-07-14T22:00:00Z"))).isEqualTo(LocalDate.of(2027, 7, 15));
    }

    @Test
    void dateOfAnInstant() {
        assertThat(GermanTime.date(Instant.parse("2026-12-31T23:30:00Z"))).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
