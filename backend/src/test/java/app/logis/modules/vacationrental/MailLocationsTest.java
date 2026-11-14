package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Objects;
import java.util.Set;

import static app.logis.modules.vacationrental.MailParsersTest.mail;
import static org.assertj.core.api.Assertions.assertThat;

/** Every value a rule reads points to the place in the mail where it is written (design.md D4). */
class MailLocationsTest {

    private final FewoUndMeerParser fewo = new FewoUndMeerParser();
    private final HoliduDsParser holidu = new HoliduDsParser();

    private static final Set<BookingField> CONFIRMATION = Set.of(BookingField.EXTERNAL_REF,
            BookingField.PROPERTY_NAME, BookingField.ARRIVAL, BookingField.DEPARTURE, BookingField.ADULTS,
            BookingField.CHILDREN, BookingField.EXPECTED_RENT, BookingField.GUEST_NAME, BookingField.GUEST_STREET,
            BookingField.GUEST_POSTAL_CODE, BookingField.GUEST_CITY, BookingField.GUEST_PHONE,
            BookingField.GUEST_EMAIL);

    private static final Set<BookingField> PERIOD = Set.of(BookingField.EXTERNAL_REF, BookingField.PROPERTY_NAME,
            BookingField.ARRIVAL, BookingField.DEPARTURE);

    @ParameterizedTest
    @CsvSource({
            "fewo-confirmation-adults.eml, CONFIRMATION",
            "fewo-confirmation-child.eml, CONFIRMATION",
            "fewo-forward-apple-plain.eml, CONFIRMATION",
            "fewo-forward-apple-html.eml, CONFIRMATION",
            "fewo-forward-inline-html.eml, CONFIRMATION",
            "fewoscout-confirmation.eml, CONFIRMATION",
            "fewo-last-minute.eml, SHORT",
            "fewo-cancellation-250905.eml, PERIOD",
            "fewo-cancellation-2025.eml, PERIOD",
            "fewo-forward-quoted-cancellation.eml, PERIOD",
            "fewo-block-notice.eml, PERIOD",
            "holidu-confirmation.eml, CONFIRMATION",
            "holidu-forward-attachment.eml, CONFIRMATION",
            "ds-vvv-confirmation.eml, CONFIRMATION"})
    void everyValuePointsToItsPlace(String name, String kind) throws Exception {
        var mail = mail(name);
        var parsed = fewo.parse(mail).or(() -> holidu.parse(mail)).orElseThrow();
        var expected = switch (kind) {
            case "CONFIRMATION" -> CONFIRMATION;
            case "PERIOD" -> PERIOD;
            default -> Set.of(BookingField.EXTERNAL_REF, BookingField.PROPERTY_NAME, BookingField.ARRIVAL,
                    BookingField.DEPARTURE, BookingField.ADULTS, BookingField.CHILDREN, BookingField.GUEST_NAME);
        };
        for (var field : expected) {
            if (value(parsed, field) != null) {
                assertThat(parsed.locations()).as(name + " " + field).containsKey(field);
            }
        }
        parsed.locations().forEach((field, at) -> {
            var written = written(mail, at);
            assertThat(matches(parsed, field, written)).as(name + " " + field + " = „" + written + "“").isTrue();
        });
    }

    static String written(MailContent mail, Location at) {
        var source = at.part() == Location.Part.SUBJECT ? mail.businessSubject() : mail.text();
        return source.substring(at.start(), at.end());
    }

    private static Object value(ParsedMail p, BookingField field) {
        var g = p.guest() == null ? Guest.EMPTY : p.guest();
        return switch (field) {
            case EXTERNAL_REF -> p.bookingRef();
            case PROPERTY_NAME -> p.propertyName();
            case ARRIVAL -> p.arrival();
            case DEPARTURE -> p.departure();
            case ADULTS -> p.adults();
            case CHILDREN -> p.children();
            case PETS -> p.pets();
            case EXPECTED_RENT -> p.expectedRent();
            case GUEST_NAME -> g.name();
            case GUEST_STREET -> g.street();
            case GUEST_POSTAL_CODE -> g.postalCode();
            case GUEST_CITY -> g.city();
            case GUEST_COUNTRY -> g.country();
            case GUEST_PHONE -> g.phone();
            case GUEST_EMAIL -> g.email();
        };
    }

    private static boolean matches(ParsedMail p, BookingField field, String written) {
        var value = value(p, field);
        return switch (field) {
            case ARRIVAL, DEPARTURE -> Objects.equals(GermanText.date(written), value);
            case ADULTS -> Objects.equals(GermanText.adults(written), value);
            case CHILDREN -> Objects.equals(GermanText.children(written), value);
            case PETS -> Objects.equals(Integer.valueOf(written), value);
            case EXPECTED_RENT -> p.expectedRent().compareTo(GermanText.amount(written.replaceAll("[^0-9.,]", ""))) == 0;
            default -> Objects.equals(written, value);
        };
    }
}
