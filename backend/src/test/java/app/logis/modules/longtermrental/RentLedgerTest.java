package app.logis.modules.longtermrental;

import app.logis.modules.longtermrental.TenancyStore.ItemKind;
import app.logis.modules.longtermrental.TenancyStore.RentItem;
import app.logis.modules.longtermrental.TenancyStore.RentTerm;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Rent due, allocation and arrears (spec rent-payments). All values are invented. */
class RentLedgerTest {

    private static RentTerm term(String from, String cold, String advance) {
        return new RentTerm(1L, LocalDate.parse(from), List.of(new RentItem(ItemKind.COLD_RENT, null, new BigDecimal(cold)),
                new RentItem(ItemKind.UTILITIES_ADVANCE, null, new BigDecimal(advance))));
    }

    private static List<BigDecimal> paid(String... amounts) {
        return java.util.Arrays.stream(amounts).map(BigDecimal::new).toList();
    }

    private static RentLedger.Account account(List<RentTerm> terms, String start, String end, List<BigDecimal> payments,
            String today) {
        return RentLedger.compute(terms, LocalDate.parse(start), end == null ? null : LocalDate.parse(end), payments,
                LocalDate.parse(today));
    }

    @Test
    void fullMonths() {
        var a = account(List.of(term("2027-03-01", "650.00", "150.00")), "2027-03-01", null, paid(), "2027-05-10");
        assertThat(a.months()).extracting(RentLedger.Month::month)
                .containsExactly(YearMonth.of(2027, 3), YearMonth.of(2027, 4), YearMonth.of(2027, 5));
        assertThat(a.months()).allSatisfy(m -> assertThat(m.due()).isEqualByComparingTo("800.00"));
        assertThat(a.arrears()).isEqualByComparingTo("2400.00");
    }

    @Test
    void startAndEndInTheMiddleOfTheMonth() {
        var start = account(List.of(term("2027-04-16", "750.00", "150.00")), "2027-04-16", null, paid(), "2027-04-20");
        assertThat(start.months()).singleElement().satisfies(m -> assertThat(m.due()).isEqualByComparingTo("450.00"));
        // Ends on 10 June: 10 of 30 days.
        var end = account(List.of(term("2027-04-01", "750.00", "150.00")), "2027-04-01", "2027-06-10", paid(),
                "2027-08-01");
        assertThat(end.months()).extracting(RentLedger.Month::due).map(BigDecimal::toPlainString)
                .containsExactly("900.00", "900.00", "300.00");
    }

    @Test
    void rentChanges() {
        var a = account(List.of(term("2027-03-01", "650.00", "150.00"), term("2027-06-01", "700.00", "150.00")),
                "2027-03-01", null, paid(), "2027-06-15");
        assertThat(a.months().get(2).due()).isEqualByComparingTo("800.00");
        assertThat(a.months().get(3).due()).isEqualByComparingTo("850.00");
    }

    @Test
    void oneMonthBehindAndPartialPayment() {
        var terms = List.of(term("2027-03-01", "650.00", "150.00"));
        var behind = account(terms, "2027-03-01", null, paid("800.00"), "2027-04-20");
        assertThat(behind.months().get(0).open()).isEqualByComparingTo("0.00");
        assertThat(behind.months().get(1).open()).isEqualByComparingTo("800.00");
        assertThat(behind.arrears()).isEqualByComparingTo("800.00");

        var partial = account(terms, "2027-03-01", null, paid("800.00", "400.00"), "2027-04-20");
        assertThat(partial.months().get(0).open()).isEqualByComparingTo("0.00");
        assertThat(partial.months().get(1).open()).isEqualByComparingTo("400.00");
    }

    @Test
    void credit() {
        var a = account(List.of(term("2027-03-01", "650.00", "150.00")), "2027-03-01", null, paid("900.00"),
                "2027-03-20");
        assertThat(a.credit()).isEqualByComparingTo("100.00");
        assertThat(a.arrears()).isEqualByComparingTo("0.00");
        assertThat(a.overdue()).isFalse();
    }

    @Test
    void overdueAfterTheThirdWorkingDay() {
        var terms = List.of(term("2027-05-01", "650.00", "150.00"));
        // May 2027 starts on a Saturday: working days are Mon 3, Tue 4, Wed 5.
        assertThat(RentLedger.thirdWorkingDay(YearMonth.of(2027, 5))).isEqualTo(LocalDate.of(2027, 5, 5));
        assertThat(account(terms, "2027-05-01", null, paid(), "2027-05-04").overdue()).isFalse();
        assertThat(account(terms, "2027-05-01", null, paid(), "2027-05-05").overdue()).isFalse();
        assertThat(account(terms, "2027-05-01", null, paid(), "2027-05-06").overdue()).isTrue();
        // A month starting on a Monday.
        assertThat(RentLedger.thirdWorkingDay(YearMonth.of(2027, 3))).isEqualTo(LocalDate.of(2027, 3, 3));
    }

    @Test
    void futureTenancyHasNothingDue() {
        var a = account(List.of(term("2027-09-01", "650.00", "150.00")), "2027-09-01", null, paid(), "2027-08-20");
        assertThat(a.months()).isEmpty();
        assertThat(a.due()).isEqualByComparingTo("0.00");
    }
}
