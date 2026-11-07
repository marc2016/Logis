package app.logis.modules.vacationrental;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Writes bookings as an iCalendar feed (RFC 5545) of all-day events from arrival through departure. */
final class CalendarFeed {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private CalendarFeed() {
    }

    static String write(String calendarName, List<Booking> bookings, boolean withGuests, Instant now) {
        var out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//logis//calendar//DE");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "X-WR-CALNAME:" + escape(calendarName));
        for (var booking : bookings) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:booking-" + booking.getId() + "@logis");
            line(out, "DTSTAMP:" + STAMP.format(now));
            line(out, "DTSTART;VALUE=DATE:" + DATE.format(booking.getArrival()));
            // DTEND is exclusive: the day after departure shows the event through the departure day.
            line(out, "DTEND;VALUE=DATE:" + DATE.format(booking.getDeparture().plusDays(1)));
            line(out, "SUMMARY:" + escape(summary(booking, withGuests)));
            line(out, "TRANSP:OPAQUE");
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    static String summary(Booking booking, boolean withGuests) {
        var review = booking.getReviewState() != ReviewState.NONE ? " ?" : "";
        if (!withGuests) {
            return "Belegt" + review;
        }
        var name = booking.getGuest().name();
        var ref = booking.getExternalRef();
        String title;
        if (name != null && !name.isBlank()) {
            title = ref == null || ref.isBlank() ? name : name + " (" + ref + ")";
        } else {
            title = ref == null || ref.isBlank() ? "Belegt" : ref;
        }
        return title + review;
    }

    /** Escapes text values: backslash, semicolon, comma and line breaks. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Appends a content line, folded at 75 octets without splitting a UTF-8 character. */
    private static void line(StringBuilder out, String content) {
        var limit = 75;
        var octets = 0;
        for (var i = 0; i < content.length(); ) {
            var cp = content.codePointAt(i);
            var size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append("\r\n ");
                octets = 1;
                limit = 75;
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }

    static LocalDate since(LocalDate today) {
        return today.minusMonths(12);
    }
}
