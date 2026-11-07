package app.logis.modules.vacationrental;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers for German notation in mails: "06.11.2026", "1.234,56", "2 Erwachsene, 1 Kind". */
final class GermanText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final Pattern ADULTS = Pattern.compile("(\\d+)\\s*Erwachsene", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHILDREN = Pattern.compile("(\\d+)\\s*Kind", Pattern.CASE_INSENSITIVE);

    private GermanText() {
    }

    static LocalDate date(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static BigDecimal amount(String text) {
        if (text == null) {
            return null;
        }
        var clean = text.trim().replace(".", "").replace(',', '.');
        try {
            return new BigDecimal(clean).setScale(2);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    static Integer adults(String text) {
        return number(ADULTS, text);
    }

    /** Children are 0 when the text names adults but no children. */
    static Integer children(String text) {
        var n = number(CHILDREN, text);
        return n != null ? n : text != null && ADULTS.matcher(text).find() ? 0 : null;
    }

    private static Integer number(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** First group of the pattern in the text, trimmed, or {@code null}. */
    static String find(Pattern pattern, String text) {
        var m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        var value = m.group(1).strip();
        return value.isEmpty() ? null : value;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
