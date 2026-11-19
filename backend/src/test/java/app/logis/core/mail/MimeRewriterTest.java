package app.logis.core.mail;

import app.logis.core.mail.SensitiveValue.Kind;
import jakarta.activation.DataHandler;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** MIME structure of the pseudonymized copy (design D4 of admin-mail-export); all values are invented. */
class MimeRewriterTest {

    private static final Session SESSION = Session.getInstance(new Properties());
    private static final List<SensitiveValue> ERIKA = List.of(
            new SensitiveValue(Kind.NAME, "Erika Musterfrau"),
            new SensitiveValue(Kind.PHONE, "0151 0000001"),
            new SensitiveValue(Kind.EMAIL, "erika@example.org"),
            new SensitiveValue(Kind.REFERENCE, "250905"));

    private final MimeRewriter rewriter = new MimeRewriter(new Pseudonymizer(new Random(7)));

    private static byte[] sample(String name) throws Exception {
        try (var in = MimeRewriterTest.class.getResourceAsStream("/mails/" + name)) {
            return in.readAllBytes();
        }
    }

    private static MimeMessage parse(byte[] eml) throws Exception {
        return new MimeMessage(SESSION, new ByteArrayInputStream(eml));
    }

    private static String text(MimeRewriter.Rewritten r) {
        return new String(r.eml(), StandardCharsets.UTF_8);
    }

    /** A booking mail as an agency might send it: QP text, base64 HTML, a PDF, tracking headers. */
    private static byte[] agencyMail() throws Exception {
        var message = new MimeMessage(SESSION);
        message.setFrom(new InternetAddress("buchung@agentur.example", "Agentur"));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, "vermieter@example.net");
        message.setReplyTo(new InternetAddress[] {new InternetAddress("erika@example.org", "Erika Musterfrau")});
        message.setSubject("Buchung 250905 – Erika Musterfrau", "UTF-8");
        message.addHeader("Received", "from mx.example.net by mail.example.net");
        message.addHeader("DKIM-Signature", "v=1; a=rsa-sha256; d=agentur.example");
        message.addHeader("ARC-Seal", "i=1; a=rsa-sha256");
        var plain = new MimeBodyPart();
        plain.setText("Gast: Erika Musterfrau\nTelefon: 0151 0000001\nAnreise 23.12.2026, Miete 840,00 EUR",
                "ISO-8859-1", "plain");
        plain.setHeader("Content-Transfer-Encoding", "quoted-printable");
        var html = new MimeBodyPart();
        html.setText("<p>Gast: <b>Erika Musterfrau</b>, <a href=\"mailto:erika@example.org\">erika@example.org</a></p>",
                "UTF-8", "html");
        html.setHeader("Content-Transfer-Encoding", "base64");
        var alternative = new MimeMultipart("alternative", plain, html);
        var alternativePart = new MimeBodyPart();
        alternativePart.setContent(alternative);
        var pdf = new MimeBodyPart();
        pdf.setDataHandler(new DataHandler(new ByteArrayDataSource("%PDF-1.4 Erika Musterfrau".getBytes(),
                "application/pdf")));
        pdf.setFileName("rechnung.pdf");
        pdf.setDisposition(Part.ATTACHMENT);
        message.setContent(new MimeMultipart("mixed", alternativePart, pdf));
        message.saveChanges();
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    private static List<Part> leaves(Part part) throws Exception {
        var result = new ArrayList<Part>();
        if (part.isMimeType("multipart/*")) {
            var mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                result.addAll(leaves(mp.getBodyPart(i)));
            }
        } else if (part.isMimeType("message/rfc822")) {
            result.add(part);
            result.addAll(leaves(new MimeMessage(SESSION, part.getInputStream())));
        } else {
            result.add(part);
        }
        return result;
    }

    @Test
    void keepsPartsCharsetsAndEncodingsAndRemovesAttachments() throws Exception {
        var rewritten = rewriter.rewrite(agencyMail(), ERIKA, Set.of("agentur.example"));
        var message = parse(rewritten.eml());

        var parts = leaves(message);
        assertThat(parts).hasSize(2);
        var plain = (MimeBodyPart) parts.get(0);
        var html = (MimeBodyPart) parts.get(1);
        assertThat(plain.getContentType()).contains("text/plain").containsIgnoringCase("iso-8859-1");
        assertThat(plain.getEncoding()).isEqualTo("quoted-printable");
        assertThat(html.getContentType()).contains("text/html").containsIgnoringCase("utf-8");
        assertThat(html.getEncoding()).isEqualTo("base64");
        assertThat(text(rewritten)).doesNotContain("application/pdf", "rechnung.pdf");

        var plainText = (String) plain.getContent();
        assertThat(plainText).contains("Gast: Gast 1", "Anreise 23.12.2026, Miete 840,00 EUR")
                .doesNotContain("Erika", "0151 0000001");
        assertThat((String) html.getContent()).contains("<b>Gast 1</b>", "gast1@example.org")
                .doesNotContain("Erika", "erika@");
    }

    @Test
    void headers() throws Exception {
        var rewritten = rewriter.rewrite(agencyMail(), ERIKA, Set.of("agentur.example"));
        var message = parse(rewritten.eml());

        assertThat(message.getSubject()).startsWith("Buchung ").contains("Gast 1").doesNotContain("250905", "Erika");
        assertThat(rewritten.subject()).isEqualTo(message.getSubject());
        assertThat(message.getFrom()[0].toString()).contains("buchung@agentur.example");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("Gast 1 <gast1@example.org>");
        assertThat(message.getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString())
                .isEqualTo("person1@example.org");
        assertThat(message.getHeader("Received")).isNull();
        assertThat(message.getHeader("DKIM-Signature")).isNull();
        assertThat(message.getHeader("ARC-Seal")).isNull();
        assertThat(message.getMessageID()).endsWith("@logis.invalid>");
    }

    @Test
    void attachedMailIsRewrittenToo() throws Exception {
        var raw = sample("holidu-forward-attachment.eml");
        var original = MailContent.parse(raw);
        var values = List.of(new SensitiveValue(Kind.NAME, "Anna Vermieterin"));

        var rewritten = rewriter.rewrite(raw, values, Set.of("ds-destinationsolutions.com"));
        var message = parse(rewritten.eml());

        var parts = leaves(message);
        assertThat(parts.stream().filter(p -> {
            try {
                return p.isMimeType("message/rfc822");
            } catch (Exception e) {
                return false;
            }
        })).hasSize(1);
        // The forwarder's address is replaced, the agency's address in the attached mail stays.
        assertThat(text(rewritten)).doesNotContain("anna@example.org", "Anna Vermieterin", "@localhost>")
                .contains("buchungsservice@ds-destinationsolutions.com");
        // The parsed content still comes from the attached mail.
        var content = MailContent.parse(rewritten.eml());
        assertThat(content.subject()).isEqualTo(original.subject());
        assertThat(content.text()).isNotBlank();
    }

    @Test
    void forwardedSamplesKeepTheirStructure() throws Exception {
        for (var name : List.of("fewo-forward-apple-html.eml", "fewo-forward-apple-plain.eml",
                "fewo-forward-inline-html.eml", "fewo-forward-quoted-cancellation.eml")) {
            var raw = sample(name);
            var before = parse(raw);
            var rewritten = rewriter.rewrite(raw, ERIKA, Set.of("fewoundmeer.de"));
            var after = parse(rewritten.eml());
            assertThat(after.getContentType().split(";")[0]).as(name).isEqualTo(before.getContentType().split(";")[0]);
            assertThat(leaves(after)).as(name).hasSameSizeAs(leaves(before));
            var content = MailContent.parse(rewritten.eml());
            assertThat(content.text()).as(name).doesNotContain("Erika Musterfrau", "0151 0000001", "erika@example.org");
        }
    }


    @Test
    void agencyDomainsLeaveOutPublicProviders() {
        assertThat(MimeRewriter.agencyDomains(List.of("buchung@fewoundmeer.de", "anna@gmail.com", "x@GMX.de", "kein")))
                .containsExactly("fewoundmeer.de");
    }
}
