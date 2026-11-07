package app.logis.modules.vacationrental;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;

/** Pure calculations for the occupancy figures: nights are split by month, money is never split. */
final class Occupancy {

    private Occupancy() {
    }

    /** Booked nights per month (index 0 = January) of the stay within the given year. */
    static int[] nightsPerMonth(LocalDate arrival, LocalDate departure, int year) {
        var nights = new int[12];
        var start = arrival.isBefore(LocalDate.of(year, 1, 1)) ? LocalDate.of(year, 1, 1) : arrival;
        var end = departure.isAfter(LocalDate.of(year + 1, 1, 1)) ? LocalDate.of(year + 1, 1, 1) : departure;
        for (var night = start; night.isBefore(end); night = night.plusDays(1)) {
            nights[night.getMonthValue() - 1]++;
        }
        return nights;
    }

    /** Whole percentage of booked nights in the available days. */
    static int rate(int nights, int days) {
        return BigDecimal.valueOf(nights * 100L).divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    static int daysInYear(int year) {
        return Year.of(year).length();
    }
}
