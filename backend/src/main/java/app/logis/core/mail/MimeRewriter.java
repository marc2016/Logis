package app.logis.core.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Writes a pseudonymized copy of a stored mail that keeps its MIME structure: text and HTML parts with their
 * charsets and transfer encodings, multiparts and attached mails. Other attachments are removed (design.md of
 * admin-mail-export, D4).
 */
class MimeRewriter {

    /** Headers that carry server and mailbox addresses and are of no use for parsing. */
    private static final List<String> DROPPED_HEADERS = List.of("Received", "Return-Path", "Delivered-To",
            "X-Original-To", "DKIM-Signature", "Authentication-Results", "Received-SPF", "X-Received", "X-Envelope-From",
            "X-Envelope-To");
    private static final List<String> ADDRESS_HEADERS = List.of("From", "Sender", "Reply-To", "To", "Cc", "Bcc");
    /** Public mail providers: a sender there is a person, not an agency. */
    private static final Set<String> PUBLIC_PROVIDERS = Set.of("gmail.com", "googlemail.com", "web.de", "gmx.de",
            "gmx.net", "gmx.at", "gmx.ch", "t-online.de", "outlook.com", "outlook.de", "hotmail.com", "hotmail.de",
            "live.com", "live.de", "yahoo.com", "yahoo.de", "icloud.com", "me.com", "mac.com", "freenet.de",
            "posteo.de", "mailbox.org", "aol.com", "arcor.de", "online.de", "magenta.de", "proton.me",
            "protonmail.com");

    private static final Session SESSION;

    static {
        var props = new Properties();
        props.setProperty("mail.mime.allowutf8", "true");
        SESSION = Session.getInstance(props);
    }

    private final Pseudonymizer pseudonymizer;

    MimeRewriter(Pseudonymizer pseudonymizer) {
        this.pseudonymizer = pseudonymizer;
    }

    /**
     * The rewritten message; throws if the mail cannot be read or written. {@code keepDomains}: sender domains of
     * agencies, whose addresses stay (see {@link #agencyDomains}).
     */
    Rewritten rewrite(byte[] raw, Collection<SensitiveValue> values, Set<String> keepDomains)
            throws MessagingException, IOException {
        var message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
        var context = new Context(values, Set.copyOf(keepDomains));
        rewriteMessage(message, context);
        message.saveChanges();
        message.setHeader("Message-ID", "<" + UUID.randomUUID() + "@logis.invalid>");
        var out = new ByteArrayOutputStream(raw.length);
        message.writeTo(out);
        var from = message.getFrom();
        return new Rewritten(out.toByteArray(), message.getSubject(),
                from == null || from.length == 0 ? null : from[0].toString());
    }

    record Rewritten(byte[] eml, String subject, String sender) {
    }

    private record Context(Collection<SensitiveValue> values, Set<String> keepDomains) {
    }

    private void rewriteMessage(MimeMessage message, Context context) throws MessagingException, IOException {
        for (var header : DROPPED_HEADERS) {
            message.removeHeader(header);
        }
        for (var name : List.copyOf(headerNames(message))) {
            if (name.toUpperCase(Locale.ROOT).startsWith("ARC-")) {
                message.removeHeader(name);
            }
        }
        var subject = message.getSubject();
        if (subject != null) {
            message.setSubject(pseudonymizer.apply(subject, context.values(), context.keepDomains(), false), "UTF-8");
        }
        for (var header : ADDRESS_HEADERS) {
            var addresses = message.getHeader(header, ",");
            if (addresses != null) {
                message.setHeader(header, rewriteAddresses(addresses, context));
            }
        }
        if (!rewritePart(message, context)) {
            // Nothing readable left: an empty text body keeps the message valid.
            message.setText("", "UTF-8");
        }
    }

    /** Returns false if the part has to be removed. */
    private boolean rewritePart(MimePart part, Context context) throws MessagingException, IOException {
        if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            var html = part.isMimeType("text/html");
            var type = new ContentType(part.getContentType());
            var charset = type.getParameter("charset") == null ? "UTF-8" : type.getParameter("charset");
            var encoding = part.getEncoding();
            var text = content(part);
            part.setText(pseudonymizer.apply(text, context.values(), context.keepDomains(), html), charset,
                    type.getSubType());
            // Keep quoted-printable and base64; for 7bit/8bit Jakarta Mail picks a valid one for the new text.
            if (encoding != null && (encoding.equalsIgnoreCase("quoted-printable") || encoding.equalsIgnoreCase("base64"))) {
                part.setHeader("Content-Transfer-Encoding", encoding);
            }
            return true;
        }
        if (part.isMimeType("multipart/*")) {
            var multipart = (Multipart) part.getContent();
            for (int i = multipart.getCount() - 1; i >= 0; i--) {
                BodyPart child = multipart.getBodyPart(i);
                if (!(child instanceof MimePart mime) || !rewritePart(mime, context)) {
                    multipart.removeBodyPart(i);
                }
            }
            if (multipart.getCount() == 0) {
                return false;
            }
            part.setContent(multipart);
            return true;
        }
        if (part.isMimeType("message/rfc822")) {
            var nested = new MimeMessage(SESSION, part.getInputStream());
            rewriteMessage(nested, context);
            nested.saveChanges();
            nested.setHeader("Message-ID", "<" + UUID.randomUUID() + "@logis.invalid>");
            part.setContent(nested, "message/rfc822");
            return true;
        }
        return false;
    }

    private String rewriteAddresses(String header, Context context) {
        try {
            var parsed = InternetAddress.parseHeader(header, false);
            var result = new ArrayList<String>();
            for (var address : parsed) {
                var personal = address.getPersonal() == null ? null
                        : pseudonymizer.apply(address.getPersonal(), context.values(), context.keepDomains(), false);
                var mail = pseudonymizer.address(address.getAddress(), context.values(), context.keepDomains());
                result.add(new InternetAddress(mail, personal, "UTF-8").toString());
            }
            return String.join(", ", result);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            // Unparseable address header: pseudonymize it as text.
            return pseudonymizer.apply(header, context.values(), context.keepDomains(), false);
        }
    }

    /**
     * The domains of these sender addresses that belong to agencies: public mail providers are left out, since a
     * sender there is a person.
     */
    static Set<String> agencyDomains(Collection<String> senders) {
        var domains = new HashSet<String>();
        for (var sender : senders) {
            if (sender != null && sender.contains("@")) {
                var domain = sender.substring(sender.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT).replace(">", "");
                if (!PUBLIC_PROVIDERS.contains(domain)) {
                    domains.add(domain);
                }
            }
        }
        return domains;
    }

    private static String content(Part part) throws MessagingException, IOException {
        var content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        try (var in = part.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static List<String> headerNames(MimeMessage message) throws MessagingException {
        var names = new ArrayList<String>();
        var headers = message.getAllHeaders();
        while (headers.hasMoreElements()) {
            names.add(headers.nextElement().getName());
        }
        return names;
    }
}
