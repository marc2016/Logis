package app.logis.modules.vacationrental;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MailRecognizerTest {

    private static AiExtraction change(String departure) {
        return new AiExtraction(true, "Fewo & Meer", "change", "250905", "Haus Seeufer - Nordhorn", "2026-11-07",
                departure, "4", "", "", "", "", "", "", "", "", "", "");
    }

    private static MailRecognizer recognizer(AiExtractor extractor) {
        var factory = extractor == null ? new StaticListableBeanFactory()
                : new StaticListableBeanFactory(Map.of("ai", extractor));
        return new MailRecognizer(List.of(new FewoUndMeerParser(), new HoliduDsParser()),
                factory.getBeanProvider(AiExtractor.class));
    }

    @Test
    void rulesWinAndTheAiIsNotCalled() throws Exception {
        var calls = new AtomicInteger();
        var r = recognizer((s, sub, t) -> {
            calls.incrementAndGet();
            return Optional.empty();
        }).recognize(MailParsersTest.mail("fewo-confirmation-adults.eml"));
        assertThat(r.source()).isEqualTo(MailRecognizer.Source.RULE);
        assertThat(calls).hasValue(0);
    }

    @Test
    void unknownMailGoesToTheAi() throws Exception {
        var r = recognizer((s, sub, t) -> Optional.of(change("2026-11-09")))
                .recognize(MailParsersTest.mail("unknown-change.eml"));
        assertThat(r.source()).isEqualTo(MailRecognizer.Source.AI);
        assertThat(r.parsed().type()).isEqualTo(ParsedMail.MailType.CHANGE);
        assertThat(r.parsed().departure()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(r.parsed().adults()).isEqualTo(4);
        assertThat(r.parsed().children()).isNull();
        assertThat(r.parsed().guest()).isNull();
    }

    @Test
    void incompleteAiResultIsNotRecognised() throws Exception {
        var r = recognizer((s, sub, t) -> Optional.of(change("")))
                .recognize(MailParsersTest.mail("unknown-change.eml"));
        assertThat(r).isEqualTo(MailRecognizer.Recognition.NONE);
        var badType = new AiExtraction(true, "X", "UMBUCHUNG", "1", "", "2026-01-01", "2026-01-02", "", "", "", "",
                "", "", "", "", "", "", "");
        assertThat(MailRecognizer.toParsed(badType)).isEmpty();
    }

    @Test
    void withoutAiUnknownMailsStayUnrecognised() throws Exception {
        var recognizer = recognizer(null);
        assertThat(recognizer.aiEnabled()).isFalse();
        assertThat(recognizer.recognize(MailParsersTest.mail("unknown-change.eml")))
                .isEqualTo(MailRecognizer.Recognition.NONE);
    }

    @Test
    void quotedPassagesBecomeLocations() throws Exception {
        var mail = MailParsersTest.mail("unknown-change.eml");
        var text = mail.text();
        // A passage that occurs in the mail, quoted with other blanks than the mail uses.
        var line = text.lines().filter(l -> l.contains("2026")).findFirst().orElseThrow();
        var quoted = line.strip().replaceAll("\\s+", "  \n ");
        var e = new AiExtraction(true, "Fewo & Meer", "change", "250905", "Haus Seeufer - Nordhorn", "2026-11-07",
                "2026-11-09", "4", "", "", "", "Erika Musterfrau", "", "", "", "", "", "",
                List.of(new AiExtraction.Quote("departure", quoted),
                        new AiExtraction.Quote("guestName", "Max Mustermann wohnt woanders"),
                        new AiExtraction.Quote("unknownField", line)));
        var r = recognizer((s, sub, t) -> Optional.of(e)).recognize(mail);
        var at = r.parsed().locations().get(BookingField.DEPARTURE);
        assertThat(at).isNotNull();
        assertThat(text.substring(at.start(), at.end())).isEqualTo(line.strip());
        // Not found: the value stays, without a location.
        assertThat(r.parsed().guest().name()).isEqualTo("Erika Musterfrau");
        assertThat(r.parsed().locations()).doesNotContainKey(BookingField.GUEST_NAME).hasSize(1);
    }

    @Test
    void rulesOnlyNeverAskTheAi() throws Exception {
        var calls = new AtomicInteger();
        var recognizer = recognizer((s, sub, t) -> {
            calls.incrementAndGet();
            return Optional.of(change("2026-11-09"));
        });
        assertThat(recognizer.recognizeByRules(MailParsersTest.mail("unknown-change.eml"))).isEmpty();
        assertThat(recognizer.recognizeByRules(MailParsersTest.mail("fewo-confirmation-adults.eml"))).isPresent();
        assertThat(calls).hasValue(0);
    }
}
