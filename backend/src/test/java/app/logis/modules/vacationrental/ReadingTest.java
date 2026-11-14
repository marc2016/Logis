package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingTest {

    @Test
    void locationCoversExactlyTheValue() {
        var text = "Kopf\nAnreise \t 23.12.2026 \nAbreise\t27.12.2026";
        var r = new Reading("Betreff", text);
        var value = r.text(Pattern.compile("(?m)^Anreise[ \\t]*(.*)$"), BookingField.ARRIVAL);
        assertThat(value).isEqualTo("23.12.2026");
        var at = r.location(BookingField.ARRIVAL);
        assertThat(at.part()).isEqualTo(Location.Part.TEXT);
        assertThat(text.substring(at.start(), at.end())).isEqualTo("23.12.2026");
    }

    @Test
    void subjectAndSharedLocations() {
        var r = new Reading("Stornierung 250905, Haus Seeufer", "2 Erwachsene, 1 Kind");
        r.subject(Pattern.compile("^Stornierung (\\S+),"), BookingField.EXTERNAL_REF);
        r.text(Pattern.compile("(.+)"), BookingField.ADULTS, BookingField.CHILDREN);
        assertThat(r.location(BookingField.EXTERNAL_REF)).isEqualTo(new Location(Location.Part.SUBJECT, 12, 18));
        assertThat(r.location(BookingField.ADULTS)).isEqualTo(r.location(BookingField.CHILDREN));
    }

    @Test
    void emptyValueHasNoLocation() {
        var r = new Reading("", "Telefon\t  \n");
        assertThat(r.text(Pattern.compile("(?m)^Telefon\\t(.*)$"), BookingField.GUEST_PHONE)).isNull();
        assertThat(r.locations()).isEmpty();
    }
}
