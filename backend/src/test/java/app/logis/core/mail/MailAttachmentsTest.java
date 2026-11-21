package app.logis.core.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Attachments of stored mails (design D3 of statement-mails). All values are invented. */
class MailAttachmentsTest {

    private static final Session SESSION = Session.getInstance(new Properties());
    private static final byte[] PDF = "%PDF-1.4\n%Test\n".getBytes(StandardCharsets.US_ASCII);

    private static MimeMessage mail(MimeBodyPart... attachments) throws Exception {
        var message = new MimeMessage(SESSION);
        message.setFrom(new InternetAddress("abrechnung@agentur.example"));
        message.setSubject("Ihre Abrechnung", "UTF-8");
        var text = new MimeBodyPart();
        text.setText("Anbei die Abrechnung.", "UTF-8");
        var multipart = new MimeMultipart("mixed", text);
        for (var a : attachments) {
            multipart.addBodyPart(a);
        }
        message.setContent(multipart);
        message.saveChanges();
        return message;
    }

    private static MimeBodyPart file(String name, String type, byte[] content) throws Exception {
        var part = new MimeBodyPart();
        part.setContent(content, type);
        part.setFileName(name);
        part.setDisposition("attachment");
        return part;
    }

    private static byte[] bytes(MimeMessage message) throws Exception {
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    @Test
    void pdfAttachment() throws Exception {
        var raw = bytes(mail(file("Abrechnung.pdf", "application/pdf", PDF)));
        assertThat(MailAttachments.of(raw)).singleElement().satisfies(a -> {
            assertThat(a.fileName()).isEqualTo("Abrechnung.pdf");
            assertThat(a.content()).isEqualTo(PDF);
            assertThat(a.isPdf()).isTrue();
        });
        assertThat(MailAttachments.firstPdf(raw)).isPresent();
    }

    @Test
    void pdfInsideAForwardedMail() throws Exception {
        var original = mail(file("Abrechnung.pdf", "application/octet-stream", PDF));
        var forwarded = new MimeBodyPart();
        forwarded.setContent(original, "message/rfc822");
        forwarded.setDisposition("attachment");
        var raw = bytes(mail(forwarded));
        assertThat(MailAttachments.firstPdf(raw)).get().extracting(MailAttachments.Attachment::content)
                .isEqualTo(PDF);
    }

    @Test
    void mailWithoutAttachment() throws Exception {
        assertThat(MailAttachments.of(bytes(mail()))).isEmpty();
        assertThat(MailAttachments.of("kein Mail".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void notAPdfAlthoughNamedSo() throws Exception {
        var raw = bytes(mail(file("Abrechnung.pdf", "application/pdf", "PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1))));
        assertThat(MailAttachments.of(raw)).hasSize(1);
        assertThat(MailAttachments.firstPdf(raw)).isEmpty();
    }
}
