package app.logis.modules.vacationrental;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads values from a mail's subject and text like {@link GermanText#find}, and remembers where each value was found
 * (design.md D4). One reading per parsed mail.
 */
final class Reading {

    private final String subject;
    private final String text;
    private final Map<BookingField, Location> locations = new EnumMap<>(BookingField.class);

    Reading(String subject, String text) {
        this.subject = subject == null ? "" : subject;
        this.text = text == null ? "" : text;
    }

    /** First group of the pattern in the text, stripped, or {@code null}; its location goes to the fields. */
    String text(Pattern pattern, BookingField... fields) {
        return find(Location.Part.TEXT, pattern.matcher(text), fields);
    }

    /** First group of the pattern in the subject, stripped, or {@code null}; its location goes to the fields. */
    String subject(Pattern pattern, BookingField... fields) {
        return find(Location.Part.SUBJECT, pattern.matcher(subject), fields);
    }

    private String find(Location.Part part, Matcher m, BookingField... fields) {
        if (!m.find()) {
            return null;
        }
        return group(part, m, 1, fields);
    }

    /** A group of a matcher on the subject or the text, stripped, or {@code null}; its location goes to the fields. */
    String group(Location.Part part, Matcher m, int group, BookingField... fields) {
        if (m.group(group) == null) {
            return null;
        }
        return value(part, m.start(group), m.end(group), fields);
    }

    /** The stripped value between the offsets of the subject or the text; its location goes to the fields. */
    String value(Location.Part part, int start, int end, BookingField... fields) {
        var source = part == Location.Part.SUBJECT ? subject : text;
        while (start < end && blank(source.charAt(start))) {
            start++;
        }
        while (end > start && blank(source.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return null;
        }
        for (var field : fields) {
            locations.put(field, new Location(part, start, end));
        }
        return source.substring(start, end);
    }

    /** Records a location found elsewhere, e.g. from a value of another field. */
    void put(BookingField field, Location location) {
        if (location != null) {
            locations.put(field, location);
        }
    }

    Location location(BookingField field) {
        return locations.get(field);
    }

    String text() {
        return text;
    }

    Map<BookingField, Location> locations() {
        return Map.copyOf(locations);
    }

    private static boolean blank(char c) {
        return Character.isWhitespace(c) || c == ' ';
    }
}
