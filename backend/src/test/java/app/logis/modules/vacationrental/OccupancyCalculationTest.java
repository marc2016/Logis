package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OccupancyCalculationTest {

    @Test
    void nightsAreSplitAcrossTheMonthBoundary() {
        var nights = Occupancy.nightsPerMonth(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 4, 4), 2026);
        assertThat(nights[2]).isEqualTo(4);
        assertThat(nights[3]).isEqualTo(3);
    }

    @Test
    void staysAreClippedToTheYear() {
        var into2026 = Occupancy.nightsPerMonth(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 3), 2026);
        assertThat(into2026[0]).isEqualTo(2);
        var outOf2026 = Occupancy.nightsPerMonth(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 3), 2026);
        assertThat(outOf2026[11]).isEqualTo(2);
        assertThat(Occupancy.nightsPerMonth(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 1), 2026))
                .containsOnly(0);
    }

    @Test
    void rateIsAWholePercentage() {
        assertThat(Occupancy.rate(11, 30)).isEqualTo(37);
        assertThat(Occupancy.rate(0, 30)).isZero();
        assertThat(Occupancy.rate(31, 31)).isEqualTo(100);
    }
}
