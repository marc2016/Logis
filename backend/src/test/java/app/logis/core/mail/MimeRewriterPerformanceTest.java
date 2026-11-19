package app.logis.core.mail;

import app.logis.core.mail.SensitiveValue.Kind;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export duration (spec mail-export, „Export duration“; design D4 of mail-export-performance): 600 mails against
 * thousands of known values must not take the product of both. All values are invented.
 */
class MimeRewriterPerformanceTest {

    private static final int MAILS = 600;
    private static final int BOOKINGS = 572;
    private static final Duration BOUND = Duration.ofSeconds(30);
    private static final Session SESSION = Session.getInstance(new Properties());

    private record Guest(String name, String street, String postalCode, String city, String phone, String email,
            String reference) {

        List<SensitiveValue> values() {
            return List.of(new SensitiveValue(Kind.NAME, name), new SensitiveValue(Kind.STREET, street),
                    new SensitiveValue(Kind.POSTAL_CODE, postalCode), new SensitiveValue(Kind.CITY, city),
                    new SensitiveValue(Kind.PHONE, phone), new SensitiveValue(Kind.EMAIL, email),
                    new SensitiveValue(Kind.REFERENCE, reference));
        }
    }

    private static List<Guest> guests() {
        var guests = new ArrayList<Guest>();
        for (int i = 0; i < BOOKINGS; i++) {
            guests.add(new Guest("Gastvorname" + i + " Gastnachname" + i, "Beispielweg " + i,
                    "%05d".formatted(10000 + i), "Beispielort" + i, "0151 %07d".formatted(i),
                    "gast" + i + "@example.net", "BN%06d".formatted(i)));
        }
        return guests;
    }

    /** A booking mail with a short text part and a large HTML part (about 40 KB). */
    private static byte[] mail(Guest guest, Random random) throws Exception {
        var message = new MimeMessage(SESSION);
        message.setFrom(new InternetAddress("buchung@agentur.example", "Agentur"));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, "vermieter@example.net");
        message.setReplyTo(new InternetAddress[] {new InternetAddress(guest.email(), guest.name())});
        message.setSubject("Buchung " + guest.reference() + " – " + guest.name(), "UTF-8");
        var plain = new MimeBodyPart();
        plain.setText("Gast: " + guest.name() + "\n" + guest.street() + ", " + guest.postalCode() + " " + guest.city()
                + "\nTelefon: " + guest.phone() + "\nAnreise 23.12.2026, Miete 840,00 EUR", "UTF-8", "plain");
        var html = new StringBuilder("<html><body><table>");
        while (html.length() < 40_000) {
            html.append("<tr><td>Leistung ").append(random.nextInt(1000)).append("</td><td>Beschreibung der Leistung ")
                    .append("mit etwas Text für die Länge</td><td>").append(random.nextInt(100)).append(",00 €</td></tr>");
        }
        html.append("<p>Gast: <b>").append(guest.name()).append("</b>, ").append(guest.email()).append("</p>")
                .append("</table></body></html>");
        var htmlPart = new MimeBodyPart();
        htmlPart.setText(html.toString(), "UTF-8", "html");
        message.setContent(new MimeMultipart("alternative", plain, htmlPart));
        message.saveChanges();
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    @Test
    void sixHundredMailsWithThousandsOfKnownValues() throws Exception {
        var random = new Random(3);
        var guests = guests();
        var values = new HashSet<SensitiveValue>();
        guests.forEach(g -> values.addAll(g.values()));
        assertThat(values).hasSizeGreaterThanOrEqualTo(4000);
        var mails = new ArrayList<byte[]>();
        for (int i = 0; i < MAILS; i++) {
            mails.add(mail(guests.get(random.nextInt(guests.size())), random));
        }
        var rewriter = new MimeRewriter(new Pseudonymizer(new Random(5)));

        var start = System.nanoTime();
        MimeRewriter.Rewritten last = null;
        for (var raw : mails) {
            last = rewriter.rewrite(raw, values, Set.of("agentur.example"));
        }
        var took = Duration.ofNanos(System.nanoTime() - start);

        System.out.printf("Rewrote %d mails against %d known values in %d ms%n", MAILS, values.size(), took.toMillis());
        assertThat(took).isLessThan(BOUND);
        assertThat(new String(last.eml(), StandardCharsets.UTF_8)).doesNotContain("Gastnachname", "Beispielort");
    }
}
