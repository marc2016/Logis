package app.logis.modules.vacationrental;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Properties;

/**
 * What parsers see of a mail: sender, subject and the text. HTML is turned into text with one line per row and
 * table cells separated by a tab, so "Label\tValue" lines read the same in HTML and plain text mails.
 */
record MailContent(String messageId, Instant receivedAt, String sender, String subject, String text) {

    private static final Session SESSION = Session.getInstance(utf8());

    private static Properties utf8() {
        var p = new Properties();
        p.setProperty("mail.mime.allowutf8", "true");
        return p;
    }

    static MailContent parse(byte[] raw) {
        try {
            var message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
            return of(message);
        } catch (MessagingException | IOException e) {
            throw new IllegalArgumentException("Unreadable mail", e);
        }
    }

    static MailContent of(Message message) throws MessagingException, IOException {
        var from = message.getFrom() != null && message.getFrom().length > 0 ? message.getFrom()[0] : null;
        var sender = from instanceof InternetAddress a ? a.getAddress() : from == null ? null : from.toString();
        var ids = message.getHeader("Message-ID");
        var messageId = ids != null && ids.length > 0 ? ids[0].trim() : null;
        var date = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
        var html = find(message, "text/html");
        var plain = find(message, "text/plain");
        var text = html != null ? htmlToText(html) : plain != null ? plain : "";
        return new MailContent(messageId, date == null ? null : date.toInstant(), sender,
                message.getSubject() == null ? "" : message.getSubject().trim(), normalize(text));
    }

    private static String find(Part part, String type) throws MessagingException, IOException {
        if (part.isMimeType(type) && !Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                var found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static String htmlToText(String html) {
        var body = Jsoup.parse(html).body();
        var out = new StringBuilder();
        render(body, out);
        return out.toString();
    }

    private static void render(Node node, StringBuilder out) {
        if (node instanceof TextNode t) {
            out.append(t.text());
            return;
        }
        if (!(node instanceof Element e)) {
            return;
        }
        switch (e.normalName()) {
            case "br" -> out.append('\n');
            case "td", "th" -> {
                if (e.elementSiblingIndex() > 0) {
                    out.append('\t');
                }
                e.childNodes().forEach(c -> render(c, out));
            }
            case "tr", "p", "div", "li", "h1", "h2", "h3", "h4", "table" -> {
                out.append('\n');
                e.childNodes().forEach(c -> render(c, out));
                out.append('\n');
            }
            default -> e.childNodes().forEach(c -> render(c, out));
        }
    }

    /** Unifies line breaks and non-breaking spaces, trims lines and drops runs of empty lines. */
    static String normalize(String text) {
        var lines = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ').split("\n");
        var out = new StringBuilder();
        var blank = false;
        for (var line : lines) {
            var trimmed = line.strip();
            if (trimmed.isEmpty()) {
                if (!blank && !out.isEmpty()) {
                    out.append('\n');
                }
                blank = true;
                continue;
            }
            blank = false;
            out.append(trimmed).append('\n');
        }
        return out.toString().strip();
    }
}
