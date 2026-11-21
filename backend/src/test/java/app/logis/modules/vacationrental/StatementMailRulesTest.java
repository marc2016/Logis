package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Recognition of statement mails (spec booking-mails, „Statement mails“; design D2). All values are invented. */
class StatementMailRulesTest {

    private static final Session SESSION = Session.getInstance(new Properties());

    static byte[] raw(String from, String subject, String html, MimeBodyPart... attachments) throws Exception {
        var message = new MimeMessage(SESSION);
        message.setFrom(new InternetAddress(from));
        message.setSubject(subject, "UTF-8");
        var body = new MimeBodyPart();
        body.setText(html, "UTF-8", "html");
        var multipart = new MimeMultipart("mixed", body);
        for (var a : attachments) {
            multipart.addBodyPart(a);
        }
        message.setContent(multipart);
        message.saveChanges();
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    private static StatementMailImport.Recognised recognised(byte[] raw) {
        return StatementMailImport.recognise(MailContent.parse(raw), raw).orElse(null);
    }

    static final String FEWO = "\"FEWO & MEER e.K.\" <info@fewoscout.com>";
    static final String NOTICE_FROM = "\"Stadtmarketing Beispielstadt e.V.\" <abrechnung@stadtmarketing.example>";

    static String noticeText(String from, String to, String invoice) {
        return "<h1>Eigentümerabrechnung</h1><p>Sehr geehrte Damen und Herren,</p><p>heute erhalten Sie Ihre Abrechnung "
                + "für den Zeitraum vom " + from + " bis zum " + to + ". Sie finden diese im Anhang dieser E-Mail als "
                + "PDF-Datei.</p><p>Rechnungsnummer: " + invoice + "</p>";
    }

    @Test
    void fewoStatement() throws Exception {
        var r = recognised(raw(FEWO, "Ihre Abrechnung für März 2027 - Haus Düne - Beispielort",
                "<p>anbei erhalten Sie die Abrechnung März 2027 für Ihr Objekt.</p>"));
        assertThat(r.agency()).isEqualTo("Fewo & Meer");
        assertThat(r.propertyName()).isEqualTo("Haus Düne - Beispielort");
        assertThat(r.periodFrom()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(r.periodTo()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(r.fewo()).isTrue();
        assertThat(recognised(raw(FEWO, "Ihre Abrechnung für Februar 2028 - Haus Düne", "<p>x</p>")).periodTo())
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void forwardedFewoStatement() throws Exception {
        var r = recognised(raw("erika@example.org", "WG: Ihre Abrechnung für Mai 2027 - Haus Düne",
                "<p>Von: FEWO &amp; MEER e.K. &lt;info@fewoscout.com&gt;</p><p>anbei die Abrechnung von Fewo & Meer</p>"));
        assertThat(r).isNotNull();
        assertThat(r.periodFrom()).isEqualTo(LocalDate.of(2027, 5, 1));
    }

    @Test
    void wrongSenderOrMonth() throws Exception {
        assertThat(recognised(raw("info@andere-agentur.example", "Ihre Abrechnung für März 2027 - Haus Düne",
                "<p>Ihre Abrechnung</p>"))).isNull();
        assertThat(recognised(raw(FEWO, "Ihre Abrechnung für Märzen 2027 - Haus Düne", "<p>x</p>"))).isNull();
        assertThat(recognised(raw(FEWO, "Neue Buchung für Haus Düne", "<p>x</p>"))).isNull();
    }

    @Test
    void statementNotice() throws Exception {
        var r = recognised(raw(NOTICE_FROM, "Ihre Abrechnung: R-100200300",
                noticeText("18.08.2027", "24.08.2027", "R-100200300")));
        assertThat(r.agency()).isEqualTo("Stadtmarketing Beispielstadt e.V.");
        assertThat(r.propertyName()).isNull();
        assertThat(r.invoiceNumber()).isEqualTo("R-100200300");
        assertThat(r.periodFrom()).isEqualTo(LocalDate.of(2027, 8, 18));
        assertThat(r.periodTo()).isEqualTo(LocalDate.of(2027, 8, 24));
        assertThat(r.fewo()).isFalse();
    }

    @Test
    void forwardedNoticeKeepsTheAgency() throws Exception {
        var html = "<p>Von: \"Stadtmarketing Beispielstadt e.V.\" &lt;abrechnung@stadtmarketing.example&gt;</p>"
                + noticeText("01.09.2027", "07.09.2027", "R-1");
        var r = recognised(raw("\"Erika Musterfrau\" <erika@example.org>", "Fwd: Ihre Abrechnung: R-1", html));
        assertThat(r.agency()).isEqualTo("Stadtmarketing Beispielstadt e.V.");
    }

    @Test
    void noticeWithoutPeriodIsNoStatement() throws Exception {
        assertThat(recognised(raw(NOTICE_FROM, "Ihre Abrechnung: R-1", "<p>Eigentümerabrechnung</p>"))).isNull();
        assertThat(recognised(raw(NOTICE_FROM, "Ihre Abrechnung: R-1", "<p>Zeitraum vom 01.09.2027 bis zum "
                + "07.09.2027</p>"))).isNull();
    }
}
