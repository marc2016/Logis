package app.logis.modules.vacationrental;

import app.logis.shared.GermanTime;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.notifications.Notification;
import app.logis.core.notifications.NotificationType;
import app.logis.core.notifications.Notifications;
import app.logis.core.sharing.ShareRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Daily at 18:00: tells every user which guests arrive and depart tomorrow on the units they can see, as one
 * notification each (spec bookings, „Arrival and departure digest“; design D9 of notifications-pushover). A row
 * per day makes sure a day is sent once, also after a restart; a missed day is not made up for.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingDigest {

    static final NotificationType ARRIVALS = NotificationType.of("vacationrental.arrivals", "Anreisen morgen",
            BookingNotifications.GROUP);
    static final NotificationType DEPARTURES = NotificationType.of("vacationrental.departures", "Abreisen morgen",
            BookingNotifications.GROUP);
    static final ZoneId ZONE = GermanTime.ZONE;
    static final int HOUR = 18;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Logger log = LoggerFactory.getLogger(BookingDigest.class);

    private final BookingRepository bookings;
    private final RentalAccess access;
    private final Notifications notifications;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    BookingDigest(BookingRepository bookings, RentalAccess access, Notifications notifications, JdbcTemplate jdbc,
            Clock clock) {
        this.bookings = bookings;
        this.access = access;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Bean
    static NotificationType arrivalsNotificationType() {
        return ARRIVALS;
    }

    @Bean
    static NotificationType departuresNotificationType() {
        return DEPARTURES;
    }

    @Scheduled(cron = "0 0 " + HOUR + " * * *", zone = "Europe/Berlin")
    void daily() {
        runSafely();
    }

    /** If Logis was down at 18:00, the digests are sent when it runs again on the same day. */
    @EventListener(ApplicationReadyEvent.class)
    void atStartup() {
        runSafely();
    }

    private void runSafely() {
        try {
            sendIfDue();
        } catch (RuntimeException e) {
            log.warn("Arrival and departure digests could not be built", e);
        }
    }

    /** Sends today's digests if it is 18:00 or later and they were not sent yet; returns whether it did. */
    boolean sendIfDue() {
        var now = clock.instant().atZone(ZONE);
        if (now.getHour() < HOUR) {
            return false;
        }
        var today = now.toLocalDate();
        // Reserves the day: a restart or a second instance finds the row and sends nothing.
        var reserved = jdbc.update("INSERT INTO vacationrental.notification_digests (day, sent_at) VALUES (?, ?) "
                + "ON CONFLICT (day) DO NOTHING", today, Timestamp.from(clock.instant()));
        if (reserved == 0) {
            return false;
        }
        var tomorrow = today.plusDays(1);
        send(ARRIVALS, bookings.findArrivalsOn(tomorrow), tomorrow, true);
        send(DEPARTURES, bookings.findDeparturesOn(tomorrow), tomorrow, false);
        return true;
    }

    private void send(NotificationType type, List<Booking> stays, LocalDate day, boolean arrivals) {
        if (stays.isEmpty()) {
            return;
        }
        var units = access.summaries(stays.stream().map(Booking::getUnitId).distinct().toList());
        // Per user the stays on the units they have a role on, in the order of the units.
        var perUser = new LinkedHashMap<Long, List<Booking>>();
        var usersByUnit = stays.stream().map(Booking::getUnitId).distinct()
                .collect(java.util.stream.Collectors.toMap(Function.identity(),
                        unitId -> access.usersOn(unitId, ShareRole.VIEWER)));
        for (var stay : stays) {
            if (!units.containsKey(stay.getUnitId())) {
                continue;
            }
            for (var userId : usersByUnit.get(stay.getUnitId())) {
                perUser.computeIfAbsent(userId, id -> new ArrayList<>()).add(stay);
            }
        }
        var messages = new LinkedHashMap<Long, Notification>();
        perUser.forEach((userId, own) -> messages.put(userId, digest(own, units, day, arrivals)));
        notifications.send(type.key(), messages);
    }

    /** The short text lists the units only; guests appear in the long text alone. */
    static Notification digest(List<Booking> stays, Map<Long, app.logis.core.realestate.UnitSummary> units,
            LocalDate day, boolean arrivals) {
        var names = stays.stream().map(b -> BookingNotifications.unitLabel(units.get(b.getUnitId()))).toList();
        var one = stays.size() == 1;
        var title = arrivals ? (one ? "1 Anreise morgen" : stays.size() + " Anreisen morgen")
                : (one ? "1 Abreise morgen" : stays.size() + " Abreisen morgen");
        var shortText = DAY.format(day) + ": " + String.join("; ", names);
        var lines = new ArrayList<String>();
        lines.add((arrivals ? "Anreisen am " : "Abreisen am ") + DAY.format(day) + ":");
        lines.add("");
        for (var stay : stays) {
            var line = new StringBuilder("– ").append(BookingNotifications.unitLabel(units.get(stay.getUnitId())));
            if (stay.getGuest() != null && stay.getGuest().name() != null) {
                line.append(": ").append(stay.getGuest().name());
            }
            line.append(" (").append(BookingNotifications.persons(stay.getAdults(), stay.getChildren())).append(", ")
                    .append(arrivals ? "bis " + DAY.format(stay.getDeparture())
                            : "seit " + DAY.format(stay.getArrival())).append(")");
            lines.add(line.toString());
        }
        // The booking list for the two days around the change-over.
        var path = BookingNotifications.BOOKINGS_PATH + "?from=" + day.minusDays(1) + "&to=" + day;
        return new Notification(title, shortText, String.join("\n", lines) + "\n", path);
    }
}
