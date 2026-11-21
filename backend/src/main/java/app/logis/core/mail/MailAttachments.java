package app.logis.core.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Attachments of a stored mail, read from its raw bytes, also those inside a forwarded mail (design D3 of
 * statement-mails). {@link MailContent} stays free of attachments: only few handlers need them.
 */
public final class MailAttachments {

    private static final Session SESSION = Session.getInstance(new Properties());
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /** An attachment with the file name the mail gives, which may be {@code null}. */
    public record Attachment(String fileName, byte[] content) {

        /** Whether the content is a PDF by its signature, whatever its name or declared type. */
        public boolean isPdf() {
            return content.length >= PDF_SIGNATURE.length
                    && Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length);
        }
    }

    private MailAttachments() {
    }

    /** RFC 2231 names are decoded by Jakarta Mail; many mailers send RFC 2047 encoded words instead. */
    private static String fileName(Part part) throws MessagingException {
        var name = part.getFileName();
        if (name == null || !name.contains("=?")) {
            return name;
        }
        try {
            return MimeUtility.decodeText(name);
        } catch (java.io.UnsupportedEncodingException e) {
            return name;
        }
    }

    /** All attachments in order of appearance; empty for an unreadable mail. */
    public static List<Attachment> of(byte[] raw) {
        var found = new ArrayList<Attachment>();
        try {
            collect(new MimeMessage(SESSION, new ByteArrayInputStream(raw)), found);
        } catch (MessagingException | IOException e) {
            return List.of();
        }
        return found;
    }

    /** The first attachment that is a PDF by its content. */
    public static java.util.Optional<Attachment> firstPdf(byte[] raw) {
        return of(raw).stream().filter(Attachment::isPdf).findFirst();
    }

    private static void collect(Part part, List<Attachment> found) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), found);
            }
            return;
        }
        if (part.isMimeType("message/rfc822") && part.getContent() instanceof Part inner) {
            collect(inner, found);
            return;
        }
        var attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
        if (!attachment || part.isMimeType("text/plain") && part.getFileName() == null) {
            return;
        }
        try (var in = part.getInputStream()) {
            found.add(new Attachment(fileName(part), in.readAllBytes()));
        }
    }
}
