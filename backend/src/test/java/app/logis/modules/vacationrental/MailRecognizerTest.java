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
}
