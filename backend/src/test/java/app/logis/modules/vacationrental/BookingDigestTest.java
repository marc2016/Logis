package app.logis.modules.vacationrental;

import app.logis.core.notifications.TestChannels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The daily digest of tomorrow's arrivals and departures (spec bookings, „Arrival and departure digest“ and „Digest
 * once per day“; design D9 of notifications-pushover). Each test moves the clock to a day of its own.
 */
@Import(TestChannels.class)
@TestPropertySource(properties = "logis.auth.public-url=https://logis.example.org")
class BookingDigestTest extends VacationRentalTest {

    private static final String[] TYPES = {"vacationrental.arrivals", "vacationrental.departures"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** Days already used by a test of this JVM, so that no test finds the day of another one. */
    private static int usedDays;

    @Autowired
    BookingDigest digest;
    @Autowired
    TestChannels.Recording recording;
    @Autowired
    TestChannels.LongRecording longRecording;

    private Actor anna;
    private LocalDate today;
    private LocalDate tomorrow;

    @BeforeEach
    void anEveningOfItsOwn() throws Exception {
        usedDays += 3;
        moveTo(LocalDate.now(BookingDigest.ZONE).plusDays(usedDays), LocalTime.of(18, 5));
        anna = actor();
        TestChannels.switchOn(jdbc, anna.id(), TYPES);
    }

    /** Sessions end over night: sign Anna in again after the clock moved on. */
    private void signInAgain() throws Exception {
        anna = new Actor(anna.user(), login(anna.user()));
    }

    private void moveTo(LocalDate day, LocalTime time) {
        var target = day.atTime(time).atZone(BookingDigest.ZONE).toInstant();
        clock.advance(Duration.between(clock.instant(), target));
        today = day;
        tomorrow = day.plusDays(1);
        jdbc.update("DELETE FROM vacationrental.notification_digests WHERE day >= ?", day);
    }

    private Long stayWithGuest(Actor actor, Long unitId, LocalDate arrival, LocalDate departure, String guest)
            throws Exception {
        var body = stay(unitId, arrival.toString(), departure.toString());
        body.put("guest", Map.of("name", guest));
        return id(book(actor, body).andExpect(status().isCreated()));
    }

    @Test
    void oneDigestPerUserListsTheUnitsAndOnlyTheLongFormNamesTheGuests() throws Exception {
        var ids = twoUnitProperty(anna);
        var carla = actor();
        var bert = actor();
        share(anna, "unit", ids[2], carla, "VIEWER");
        TestChannels.switchOn(jdbc, carla.id(), TYPES);
        TestChannels.switchOn(jdbc, bert.id(), TYPES);
        stayWithGuest(anna, ids[1], tomorrow, tomorrow.plusDays(4), "Erika Musterfrau");
        stayWithGuest(anna, ids[2], tomorrow, tomorrow.plusDays(2), "Jonas Beispiel");
        stayWithGuest(anna, ids[2], tomorrow.minusDays(5), tomorrow, "Max Mustermann");
        var house = jdbc.queryForObject("SELECT name FROM core.properties WHERE id = ?", String.class, ids[0]);

        assertThat(digest.sendIfDue()).isTrue();

        var sent = recording.await(anna.id(), 2);
        var arrivals = sent.stream().filter(s -> s.title().contains("Anreise")).findFirst().orElseThrow();
        assertThat(arrivals.title()).isEqualTo("2 Anreisen morgen");
        assertThat(arrivals.text()).isEqualTo(DAY.format(tomorrow) + ": " + house + ", Whg. 1; " + house + ", Whg. 2");
        assertThat(arrivals.link()).isEqualTo("https://logis.example.org/ferienvermietung/buchungen?from=" + today
                + "&to=" + tomorrow);
        var departures = sent.stream().filter(s -> s.title().contains("Abreise")).findFirst().orElseThrow();
        assertThat(departures.title()).isEqualTo("1 Abreise morgen");
        assertThat(departures.text()).isEqualTo(DAY.format(tomorrow) + ": " + house + ", Whg. 2");
        assertThat(sent).allSatisfy(s -> assertThat(s.title() + s.text()).doesNotContain("Musterfrau")
                .doesNotContain("Beispiel").doesNotContain("Mustermann"));
        var mails = longRecording.await(anna.id(), 2);
        assertThat(mails.stream().filter(s -> s.title().contains("Anreise")).findFirst().orElseThrow().text())
                .contains("Whg. 1: Erika Musterfrau (2 Erwachsene, bis " + DAY.format(tomorrow.plusDays(4)) + ")")
                .contains("Whg. 2: Jonas Beispiel");
        assertThat(mails.stream().filter(s -> s.title().contains("Abreise")).findFirst().orElseThrow().text())
                .contains("Whg. 2: Max Mustermann (2 Erwachsene, seit " + DAY.format(tomorrow.minusDays(5)) + ")");

        // Carla sees "Whg. 2" only; Bert has no role and gets nothing.
        var carlas = recording.await(carla.id(), 2);
        assertThat(carlas).allSatisfy(s -> assertThat(s.text()).contains("Whg. 2").doesNotContain("Whg. 1"));
        assertThat(carlas).extracting(TestChannels.Sent::title)
                .containsExactlyInAnyOrder("1 Anreise morgen", "1 Abreise morgen");
        recording.await(bert.id(), 0);
    }

    @Test
    void cancelledUnconfirmedAndOwnerStaysAndOtherDaysAreLeftOut() throws Exception {
        var ids = twoUnitProperty(anna);
        var other = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        var cancelled = booking(anna, ids[1], tomorrow.toString(), tomorrow.plusDays(2).toString());
        var unconfirmed = booking(anna, ids[2], tomorrow.toString(), tomorrow.plusDays(2).toString());
        var ownerUse = booking(anna, other, tomorrow.toString(), tomorrow.plusDays(2).toString());
        booking(anna, ids[1], tomorrow.plusDays(3).toString(), tomorrow.plusDays(5).toString());
        jdbc.update("UPDATE vacationrental.bookings SET cancelled = true WHERE id = ?", cancelled);
        jdbc.update("UPDATE vacationrental.bookings SET review_state = 'NEW' WHERE id = ?", unconfirmed);
        jdbc.update("UPDATE vacationrental.bookings SET kind = 'OWNER', adults = 0 WHERE id = ?", ownerUse);

        assertThat(digest.sendIfDue()).isTrue();

        recording.await(anna.id(), 0);
    }

    @Test
    void sentOnceADayAlsoAfterARestartAndNotBeforeSix() throws Exception {
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        booking(anna, unit, tomorrow.toString(), tomorrow.plusDays(2).toString());

        // The scheduler at 18:00, then a restart at 18:30.
        digest.daily();
        recording.await(anna.id(), 1);
        clock.advance(Duration.ofMinutes(25));
        digest.atStartup();
        assertThat(digest.sendIfDue()).isFalse();
        recording.await(anna.id(), 1);

        // The next morning nothing is due yet.
        moveTo(tomorrow, LocalTime.of(9, 0));
        signInAgain();
        booking(anna, unit, tomorrow.plusDays(1).toString(), tomorrow.plusDays(3).toString());
        digest.atStartup();
        assertThat(digest.sendIfDue()).isFalse();
        recording.await(anna.id(), 1);
    }

    @Test
    void downAtSixItIsSentAfterTheStartOnTheSameDayButAMissedDayIsNotMadeUpFor() throws Exception {
        var unit = unitIds(singleUnitProperty(anna, "Haus Waldeck")).getFirst();
        // Logis was down at 18:00 on the first day and comes back the next morning: that day stays missed.
        booking(anna, unit, tomorrow.toString(), tomorrow.plusDays(1).toString());
        moveTo(tomorrow, LocalTime.of(7, 30));
        signInAgain();
        digest.atStartup();
        recording.await(anna.id(), 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vacationrental.notification_digests WHERE day = ?",
                Integer.class, today.minusDays(1))).isZero();

        // Down at 18:00 again, started at 19:10 on the same day: sent after the start.
        booking(anna, unit, tomorrow.toString(), tomorrow.plusDays(2).toString());
        clock.advance(Duration.between(LocalTime.of(7, 30), LocalTime.of(19, 10)));
        digest.atStartup();
        var sent = recording.await(anna.id(), 1).get(0);
        assertThat(sent.title()).isEqualTo("1 Anreise morgen");
        assertThat(sent.text()).isEqualTo(DAY.format(tomorrow) + ": Haus Waldeck");
    }
}
