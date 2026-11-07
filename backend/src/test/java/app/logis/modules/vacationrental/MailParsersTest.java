package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MailParsersTest {

    private final FewoUndMeerParser fewo = new FewoUndMeerParser();
    private final HoliduDsParser holidu = new HoliduDsParser();

    static MailContent mail(String name) throws IOException {
        try (var in = MailParsersTest.class.getResourceAsStream("/mails/" + name)) {
            return MailContent.parse(in.readAllBytes());
        }
    }

    @Test
    void fewoConfirmationWithAdultsOnly() throws Exception {
        var p = fewo.parse(mail("fewo-confirmation-adults.eml")).orElseThrow();
        assertThat(p.agency()).isEqualTo("Fewo & Meer");
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.CONFIRMATION);
        assertThat(p.bookingRef()).isEqualTo("275505");
        assertThat(p.propertyName()).isEqualTo("Haus Seeufer - Nordhorn");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(p.departure()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(p.adults()).isEqualTo(4);
        assertThat(p.children()).isZero();
        assertThat(p.expectedRent()).isEqualByComparingTo("248.58");
        assertThat(p.guest()).isEqualTo(new Guest("Erika Musterfrau", "Musterweg 1", "12345", "Musterstadt",
                "Deutschland", "0151 0000001", "erika@example.org"));
        assertThat(p.usable()).isTrue();
    }

    @Test
    void fewoConfirmationWithChildAndOnlyMobile() throws Exception {
        var p = fewo.parse(mail("fewo-confirmation-child.eml")).orElseThrow();
        assertThat(p.bookingRef()).isEqualTo("275031");
        assertThat(p.adults()).isEqualTo(2);
        assertThat(p.children()).isEqualTo(1);
        assertThat(p.expectedRent()).isEqualByComparingTo("559.00");
        assertThat(p.guest().phone()).isEqualTo("0172 0000002");
        assertThat(p.guest().city()).isEqualTo("Probestadt");
    }

    @Test
    void fewoShortNotice() throws Exception {
        var p = fewo.parse(mail("fewo-last-minute.eml")).orElseThrow();
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.SHORT_NOTICE);
        assertThat(p.bookingRef()).isEqualTo("275505");
        assertThat(p.propertyName()).isEqualTo("Haus Seeufer - Nordhorn");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(p.adults()).isEqualTo(4);
        assertThat(p.children()).isZero();
        assertThat(p.pets()).isZero();
        assertThat(p.expectedRent()).isNull();
        assertThat(p.guest().phone()).isEqualTo("+49 151 0000001");
    }

    @Test
    void fewoCancellations() throws Exception {
        for (var name : new String[] {"fewo-cancellation-2025.eml", "fewo-cancellation-275505.eml"}) {
            var p = fewo.parse(mail(name)).orElseThrow();
            assertThat(p.type()).isEqualTo(ParsedMail.MailType.CANCELLATION);
            assertThat(p.usable()).isTrue();
        }
        var p = fewo.parse(mail("fewo-cancellation-2025.eml")).orElseThrow();
        assertThat(p.bookingRef()).isEqualTo("256456");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2025, 9, 23));
        assertThat(p.departure()).isEqualTo(LocalDate.of(2025, 9, 26));
    }

    @Test
    void holiduConfirmationUsesTheTotalPrice() throws Exception {
        var p = holidu.parse(mail("holidu-confirmation.eml")).orElseThrow();
        assertThat(p.agency()).isEqualTo("Holidu");
        assertThat(p.bookingRef()).isEqualTo("6a31h227wy");
        assertThat(p.propertyName()).isEqualTo("Ferienhaus Seeufer");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2026, 12, 4));
        assertThat(p.departure()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(p.adults()).isEqualTo(2);
        assertThat(p.children()).isZero();
        assertThat(p.expectedRent()).isEqualByComparingTo("309.08");
        assertThat(p.guest()).isEqualTo(new Guest("Anna Probe", "Ackerweg 2", "45701", "Beispielstadt",
                "Deutschland", "004915200000003", "gast-0003@guest.holidu.com"));
    }

    @Test
    void unknownMailsAreLeftToTheFallback() throws Exception {
        assertThat(fewo.parse(mail("unknown-change.eml"))).isEmpty();
        assertThat(holidu.parse(mail("unknown-change.eml"))).isEmpty();
        assertThat(fewo.parse(mail("holidu-confirmation.eml"))).isEmpty();
        assertThat(holidu.parse(mail("fewo-confirmation-adults.eml"))).isEmpty();
    }

    @Test
    void forwardedInlineHtmlConfirmation() throws Exception {
        var mail = mail("fewo-forward-inline-html.eml");
        assertThat(mail.sender()).isEqualTo("anna@example.org");
        assertThat(mail.businessSubject()).isEqualTo("Neue Buchung für Haus Seeufer - Nordhorn");
        var p = fewo.parse(mail).orElseThrow();
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.CONFIRMATION);
        assertThat(p.bookingRef()).isEqualTo("275505");
        assertThat(p.expectedRent()).isEqualByComparingTo("248.58");
        assertThat(p.guest().email()).isEqualTo("erika@example.org");
    }

    @Test
    void forwardedQuotedCancellation() throws Exception {
        var mail = mail("fewo-forward-quoted-cancellation.eml");
        assertThat(mail.text()).contains("Sehr geehrter Gast").doesNotContain("> Sehr");
        var p = fewo.parse(mail).orElseThrow();
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.CANCELLATION);
        assertThat(p.bookingRef()).isEqualTo("275505");
    }

    @Test
    void confirmationForwardedAsAttachment() throws Exception {
        var mail = mail("holidu-forward-attachment.eml");
        assertThat(mail.messageId()).isEqualTo("<fwd-attachment-6a31h227wy@example.org>");
        assertThat(mail.sender()).isEqualTo("buchungsservice@ds-destinationsolutions.com");
        var p = holidu.parse(mail).orElseThrow();
        assertThat(p.bookingRef()).isEqualTo("6a31h227wy");
        assertThat(p.expectedRent()).isEqualByComparingTo("309.08");
    }

    @Test
    void germanAmounts() {
        assertThat(GermanText.amount("1.559,00")).isEqualByComparingTo(new BigDecimal("1559.00"));
        assertThat(GermanText.amount("abc")).isNull();
        assertThat(GermanText.children("4 Erwachsene/ 0 Kinder")).isZero();
    }
}
