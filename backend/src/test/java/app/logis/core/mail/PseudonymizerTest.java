package app.logis.core.mail;

import app.logis.core.mail.SensitiveValue.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Replacement rules of the admin export (design D5 of admin-mail-export); all values are invented. */
class PseudonymizerTest {

    private final Pseudonymizer pseudonymizer = new Pseudonymizer(new Random(42));

    private static final List<SensitiveValue> ERIKA = List.of(
            new SensitiveValue(Kind.NAME, "Erika Musterfrau"),
            new SensitiveValue(Kind.STREET, "Musterweg 1"),
            new SensitiveValue(Kind.POSTAL_CODE, "12345"),
            new SensitiveValue(Kind.CITY, "Beispielstadt"),
            new SensitiveValue(Kind.PHONE, "0151 0000001"),
            new SensitiveValue(Kind.EMAIL, "erika@example.org"),
            new SensitiveValue(Kind.REFERENCE, "250905"));

    private String apply(String text) {
        return pseudonymizer.apply(text, ERIKA, Set.of("fewoundmeer.de"), false);
    }

    @Test
    void knownValuesAreReplaced() {
        var result = apply("Gast: Erika Musterfrau, Musterweg 1, 12345 Beispielstadt, Tel. 0151 0000001, "
                + "erika@example.org, Buchung 250905");
        assertThat(result).doesNotContain("Erika", "Musterfrau", "Musterweg 1", "12345", "Beispielstadt",
                "0151 0000001", "erika@example.org", "250905");
        assertThat(result).contains("Gast 1", "Musterstraße 1", "Musterstadt 1", "gast1@example.org");
    }

    @Test
    void sameValueSameReplacementAcrossTexts() {
        var first = apply("Buchung 250905 von Erika Musterfrau");
        var second = apply("Stornierung 250905, Gast Erika Musterfrau");
        var ref = first.replaceAll(".*Buchung (\\S+) .*", "$1");
        assertThat(ref).matches("\\d{6}").isNotEqualTo("250905");
        assertThat(second).contains("Stornierung " + ref).contains("Gast 1");
    }

    @Test
    void namePartsAreReplaced() {
        assertThat(apply("Sehr geehrte Frau Musterfrau")).isEqualTo("Sehr geehrte Frau Gast1");
    }

    @Test
    void wordBoundariesAreKept() {
        // "Erika" inside another word stays.
        assertThat(apply("Erikastraße")).isEqualTo("Erikastraße");
    }

    @Test
    void htmlEscapedValues() {
        var values = List.of(new SensitiveValue(Kind.NAME, "Jürgen Müller & Söhne"));
        var result = pseudonymizer.apply("<td>J&uuml;rgen M&uuml;ller &amp; S&ouml;hne</td>", values, Set.of(), true);
        assertThat(result).doesNotContain("rgen", "ller").contains("Gast 1");
    }

    @Test
    void formatIsKeptForPhonesPostalCodesAndReferences() {
        var result = apply("0151 0000001 / 12345 / 250905");
        assertThat(result).matches("0\\d{3} \\d{7} / \\d{5} / \\d{6}");
    }

    @Test
    void emailAddressesByPattern() {
        var result = apply("Kontakt: max@example.net, buchung@fewoundmeer.de, max@example.net");
        assertThat(result).isEqualTo("Kontakt: person1@example.org, buchung@fewoundmeer.de, person1@example.org");
        // Also on the example domain, unless it is a replacement itself.
        assertThat(apply("Gast schreibt von moritz@example.org")).contains("person2@example.org");
    }

    @Test
    void phonesAndIbansByPattern() {
        var result = apply("Tel. +49 (0) 30 123456-78, Handy 0170/9876543, IBAN DE89 3704 0044 0532 0130 00");
        assertThat(result).doesNotContain("123456-78", "9876543", "3704 0044");
        assertThat(result).containsPattern("\\+\\d{2} \\(\\d\\) \\d{2} \\d{6}-\\d{2}");
        assertThat(result).containsPattern("IBAN DE\\d{2} \\d{4} \\d{4} \\d{4} \\d{4} \\d{2}");
    }

    @Test
    void datesAndAmountsStay() {
        var text = "Anreise 23.12.2026, Abreise 02.01.2027, Miete 1.234,56 €, Kaution 300,00 EUR, 2 Erwachsene";
        assertThat(apply(text)).isEqualTo(text);
    }

    @Test
    void headerAddresses() {
        assertThat(pseudonymizer.address("erika@example.org", ERIKA, Set.of())).isEqualTo("gast1@example.org");
        assertThat(pseudonymizer.address("buchung@fewoundmeer.de", ERIKA, Set.of("fewoundmeer.de")))
                .isEqualTo("buchung@fewoundmeer.de");
        assertThat(pseudonymizer.address("max@example.net", ERIKA, Set.of())).isEqualTo("person1@example.org");
    }

    @Test
    void fullNameWinsOverItsParts() {
        assertThat(apply("Gast Erika Musterfrau reist an")).isEqualTo("Gast Gast 1 reist an");
    }

    @Test
    void insertedReplacementIsNotReplacedAgain() {
        // „Gast“ is a known name part of another guest; the „Gast“ in „Gast 1“ must stay (spec „Overlapping values“).
        var values = List.of(new SensitiveValue(Kind.NAME, "Erika Musterfrau"), new SensitiveValue(Kind.NAME, "Hans Gast"));
        assertThat(pseudonymizer.apply("Liebe Erika Musterfrau, lieber Hans Gast", values, Set.of(), false))
                .isEqualTo("Liebe Gast 1, lieber Gast 2");
    }

    @Test
    void overlappingValuesTheFirstOneWins() {
        var values = List.of(new SensitiveValue(Kind.NAME, "Erika Musterfrau"),
                new SensitiveValue(Kind.STREET, "Musterfrau Allee 5"));
        assertThat(pseudonymizer.apply("Erika Musterfrau Allee 5", values, Set.of(), false))
                .isEqualTo("Gast 1 Allee 5");
    }

    @Test
    void umlautsAtTheStartAndEnd() {
        var values = List.of(new SensitiveValue(Kind.NAME, "Jürgen Öztürk"));
        assertThat(pseudonymizer.apply("Öztürk", values, Set.of(), false)).isEqualTo("Gast1");
        assertThat(pseudonymizer.apply("Jürgen Öztürk", values, Set.of(), false)).isEqualTo("Gast 1");
        assertThat(pseudonymizer.apply("Öztürks Jürgenhof", values, Set.of(), false)).isEqualTo("Öztürks Jürgenhof");
    }

    @Test
    void oneIndexForManyTexts() {
        var known = pseudonymizer.index(ERIKA, false);
        assertThat(pseudonymizer.apply("Erika Musterfrau", known, Set.of())).isEqualTo("Gast 1");
        assertThat(pseudonymizer.apply("Frau Musterfrau", known, Set.of())).isEqualTo("Frau Gast1");
        assertThat(pseudonymizer.address("ERIKA@example.org", known, Set.of())).isEqualTo("gast1@example.org");
    }
}
