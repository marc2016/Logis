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
    void fewoConfirmationFromFewoScoutWithoutSignature() throws Exception {
        var mail = mail("fewoscout-confirmation.eml");
        assertThat(mail.sender()).isEqualTo("info@fewoscout.com");
        assertThat(mail.text()).doesNotContain("Fewo & Meer");
        var p = fewo.parse(mail).orElseThrow();
        assertThat(p.agency()).isEqualTo("Fewo & Meer");
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.CONFIRMATION);
        assertThat(p.bookingRef()).isEqualTo("274907");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(p.departure()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(p.adults()).isEqualTo(2);
        assertThat(p.expectedRent()).isEqualByComparingTo("454.32");
        assertThat(p.usable()).isTrue();
    }

    @Test
    void holiduKeepsItsAgencyFromTheIntermediaryLine() {
        assertThat(HoliduDsParser.agency("Holidu GmbH in Kooperation mit \"DS Destination Solutions\""))
                .isEqualTo("Holidu");
        assertThat(HoliduDsParser.agency(null)).isEqualTo("Holidu");
        assertThat(HoliduDsParser.agency("Ferienbüro Muster in Kooperation mit \"DS Destination Solutions\""))
                .isEqualTo("Ferienbüro Muster");
        assertThat(HoliduDsParser.agency("x".repeat(80))).hasSize(64);
    }

    @Test
    void dsConfirmationOfVvvNordhorn() throws Exception {
        var p = holidu.parse(mail("ds-vvv-confirmation.eml")).orElseThrow();
        assertThat(p.agency()).isEqualTo("VVV-Stadt- und Citymarketing Nordhorn e.V.");
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.CONFIRMATION);
        assertThat(p.bookingRef()).isEqualTo("6a29b55l9j");
        assertThat(p.propertyName()).isEqualTo("Ferienhaus Seeufer");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2027, 5, 15));
        assertThat(p.departure()).isEqualTo(LocalDate.of(2027, 5, 18));
        assertThat(p.adults()).isEqualTo(2);
        assertThat(p.children()).isEqualTo(2);
        assertThat(p.expectedRent()).isEqualByComparingTo("447.00");
        assertThat(p.guest().name()).isEqualTo("Max Probe");
        assertThat(fewo.parse(mail("ds-vvv-confirmation.eml"))).isEmpty();
    }

    @Test
    void dsConfirmationWithoutKnownSenderIsRecognisedByTheIntermediaryLine() throws Exception {
        var raw = new String(MailParsersTest.class.getResourceAsStream("/mails/ds-vvv-confirmation.eml")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("buchungsservice@ds-destinationsolutions.com", "anna@example.org");
        var p = holidu.parse(MailContent.parse(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .orElseThrow();
        assertThat(p.agency()).isEqualTo("VVV-Stadt- und Citymarketing Nordhorn e.V.");
    }

    @Test
    void fewoBlockNotice() throws Exception {
        var p = fewo.parse(mail("fewo-block-notice.eml")).orElseThrow();
        assertThat(p.agency()).isEqualTo("Fewo & Meer");
        assertThat(p.type()).isEqualTo(ParsedMail.MailType.BLOCK_NOTICE);
        assertThat(p.bookingRef()).isEqualTo("275206");
        assertThat(p.propertyName()).isEqualTo("Haus Seeufer - Nordhorn");
        assertThat(p.arrival()).isEqualTo(LocalDate.of(2027, 5, 15));
        assertThat(p.departure()).isEqualTo(LocalDate.of(2027, 5, 18));
        assertThat(p.intermediary()).isEqualTo("VVV Nordhorn");
        assertThat(p.adults()).isNull();
        assertThat(p.guest()).isNull();
        assertThat(p.usable()).isTrue();
    }

    @Test
    void fewoBlockNoticeVariants() throws Exception {
        var spaced = blockNotice("info@fewoscout.com",
                "Neue Blockung für Haus Seeufer - Nordhorn, 275206, vom 15.05.2027 bis 18.05.2027");
        var p = fewo.parse(spaced).orElseThrow();
        assertThat(p.bookingRef()).isEqualTo("275206");
        assertThat(p.propertyName()).isEqualTo("Haus Seeufer - Nordhorn");
        assertThat(p.intermediary()).isNull();

        var forwarded = blockNotice("anna@example.org",
                "WG: Neue Blockung für Haus Seeufer - Nordhorn,275206, vom 15.05.2027 bis 18.05.2027 durch den VVV Nordhorn");
        var f = fewo.parse(forwarded).orElseThrow();
        assertThat(f.type()).isEqualTo(ParsedMail.MailType.BLOCK_NOTICE);
        assertThat(f.bookingRef()).isEqualTo("275206");
        assertThat(f.intermediary()).isEqualTo("VVV Nordhorn");
    }

    private static MailContent blockNotice(String sender, String subject) {
        var raw = "From: " + sender + "\r\nTo: vermieter@example.org\r\nSubject: " + subject
                + "\r\nMessage-ID: <variant@example.org>\r\nMIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n"
                + "Sehr geehrter Eigentümer,\r\n\r\nViele Grüße\r\n\r\nFewo & Meer e.K.\r\n";
        return MailContent.parse(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void germanAmounts() {
        assertThat(GermanText.amount("1.559,00")).isEqualByComparingTo(new BigDecimal("1559.00"));
        assertThat(GermanText.amount("abc")).isNull();
        assertThat(GermanText.children("4 Erwachsene/ 0 Kinder")).isZero();
    }
}
