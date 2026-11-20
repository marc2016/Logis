package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static app.logis.modules.vacationrental.GermanText.amount;
import static app.logis.modules.vacationrental.GermanText.blankToNull;
import static app.logis.modules.vacationrental.GermanText.date;

/** Rules for Fewo & Meer: confirmation, short-notice notice and cancellation. */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
@Order(1)
class FewoUndMeerParser implements MailParser {

    static final String AGENCY = "Fewo & Meer";

    private static final String DATE = "(\\d{2}\\.\\d{2}\\.\\d{4})";
    private static final Pattern CONFIRMATION_SUBJECT = Pattern.compile("^Neue Buchung für (.+)$");
    private static final Pattern SHORT_NOTICE_SUBJECT = Pattern.compile(
            "^Last-Minute Buchung (\\S+), (.+?), vom " + DATE + " bis " + DATE);
    private static final Pattern CANCELLATION_SUBJECT = Pattern.compile(
            "^Stornierung (\\S+), (.+?), vom " + DATE + " bis zum " + DATE);
    // "Neue Blockung für Haus Seeufer - Nordhorn,250904, vom 15.05.2027 bis 18.05.2027 durch den VVV Nordhorn"
    private static final Pattern BLOCK_NOTICE_SUBJECT = Pattern.compile(
            "^Neue Blockung für (.+?),\\s*(\\S+), vom " + DATE + " bis " + DATE
                    + "(?: durch (?:den |die |das )?(.+))?$");

    // "Eigentümerblockung 275001, Haus Seeufer - Nordhorn, vom 09.06.2027 bis 14.06.2027"
    private static final Pattern OWNER_BLOCK_SUBJECT = Pattern.compile(
            "^Eigentümerblockung (\\S+), (.+?), vom " + DATE + " bis " + DATE);

    @Override
    public Optional<ParsedMail> parse(MailContent mail) {
        if (!fromAgency(mail)) {
            return Optional.empty();
        }
        var subject = mail.businessSubject();
        var r = new Reading(subject, mail.text());
        var m = CONFIRMATION_SUBJECT.matcher(subject);
        if (m.find()) {
            var property = r.group(Location.Part.SUBJECT, m, 1, BookingField.PROPERTY_NAME);
            return Optional.of(confirmation(property, r));
        }
        m = SHORT_NOTICE_SUBJECT.matcher(subject);
        if (m.find()) {
            return Optional.of(shortNotice(m, r));
        }
        m = CANCELLATION_SUBJECT.matcher(subject);
        if (m.find()) {
            var ref = r.group(Location.Part.SUBJECT, m, 1, BookingField.EXTERNAL_REF);
            var property = r.group(Location.Part.SUBJECT, m, 2, BookingField.PROPERTY_NAME);
            var from = r.group(Location.Part.SUBJECT, m, 3, BookingField.ARRIVAL);
            var to = r.group(Location.Part.SUBJECT, m, 4, BookingField.DEPARTURE);
            return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.CANCELLATION, ref, property, date(from),
                    date(to), null, null, null, null, null).withLocations(r.locations()));
        }
        m = BLOCK_NOTICE_SUBJECT.matcher(subject);
        if (m.find()) {
            var property = r.group(Location.Part.SUBJECT, m, 1, BookingField.PROPERTY_NAME);
            var ref = r.group(Location.Part.SUBJECT, m, 2, BookingField.EXTERNAL_REF);
            var from = r.group(Location.Part.SUBJECT, m, 3, BookingField.ARRIVAL);
            var to = r.group(Location.Part.SUBJECT, m, 4, BookingField.DEPARTURE);
            return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.BLOCK_NOTICE, ref, property, date(from),
                    date(to), null, null, null, null, null, blankToNull(m.group(5))).withLocations(r.locations()));
        }
        m = OWNER_BLOCK_SUBJECT.matcher(subject);
        if (m.find()) {
            var ref = r.group(Location.Part.SUBJECT, m, 1, BookingField.EXTERNAL_REF);
            var property = r.group(Location.Part.SUBJECT, m, 2, BookingField.PROPERTY_NAME);
            var from = r.group(Location.Part.SUBJECT, m, 3, BookingField.ARRIVAL);
            var to = r.group(Location.Part.SUBJECT, m, 4, BookingField.DEPARTURE);
            return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.OWNER_BLOCK, ref, property, date(from),
                    date(to), null, null, null, null, null).withLocations(r.locations()));
        }
        return extension(subject, mail.text());
    }

    // "Zeitraum ursprüngliche Buchung: 14.09.2027 — 17.09.2027" / "Verlängerung: 12.09.2027 — 14.09.2027"
    private static final String DASH = "[ \\t\\u00a0]*[-\\u2013\\u2014][ \\t\\u00a0]*";
    private static final Pattern EXTENSION_NOTICE = Pattern.compile("Verlängerungsbuchung");
    private static final Pattern ORIGINAL_PERIOD = Pattern.compile(
            "(?m)^[ \\t\\u00a0]*Zeitraum ursprüngliche Buchung:[ \\t\\u00a0]*" + DATE + DASH + DATE);
    private static final Pattern EXTENSION_PERIOD = Pattern.compile(
            "(?m)^[ \\t\\u00a0]*Verlängerung:[ \\t\\u00a0]*" + DATE + DASH + DATE);
    private static final Pattern OBJECT_LINE = Pattern.compile("(?m)^[ \\t\\u00a0]*Objekt:[ \\t\\u00a0]*(.+?)[ \\t\\u00a0]*$");
    private static final Pattern PERSONS_LINE = Pattern.compile("(?m)^[ \\t\\u00a0]*Personen:[ \\t\\u00a0]*(.+?)[ \\t\\u00a0]*$");

    /**
     * „Information über eine Verlängerungsbuchung“ (spec booking-mails, „Extension notices“; design D1 of
     * fewo-extension-mails): recognised by its text, since its subject is often a ticket subject.
     */
    private static Optional<ParsedMail> extension(String subject, String text) {
        // Quoted lines are blanked, not removed, so that the locations still fit the text.
        var own = blankQuotedLines(text);
        if (!EXTENSION_NOTICE.matcher(own).find()) {
            return Optional.empty();
        }
        var original = ORIGINAL_PERIOD.matcher(own);
        var extension = EXTENSION_PERIOD.matcher(own);
        if (!original.find() || !extension.find()) {
            return Optional.empty();
        }
        var originalFrom = date(original.group(1));
        var originalTo = date(original.group(2));
        var extensionFrom = date(extension.group(1));
        var extensionTo = date(extension.group(2));
        if (originalFrom == null || originalTo == null || extensionFrom == null || extensionTo == null) {
            return Optional.empty();
        }
        var r = new Reading(subject, own);
        // The extended stay: the earlier arrival and the later departure of both periods.
        var arrivalFromExtension = extensionFrom.isBefore(originalFrom);
        r.group(Location.Part.TEXT, arrivalFromExtension ? extension : original, 1, BookingField.ARRIVAL);
        var departureFromExtension = extensionTo.isAfter(originalTo);
        r.group(Location.Part.TEXT, departureFromExtension ? extension : original, 2, BookingField.DEPARTURE);
        var property = r.text(OBJECT_LINE, BookingField.PROPERTY_NAME);
        var persons = r.text(PERSONS_LINE, BookingField.ADULTS, BookingField.CHILDREN);
        // "(3) Erwachsene/ (0) Kinder" and "( 3 ) Erwachsene/ ( 0 ) Kinder"
        var counts = persons == null ? null : persons.replaceAll("[()]", "");
        return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.EXTENSION, null, property,
                arrivalFromExtension ? extensionFrom : originalFrom, departureFromExtension ? extensionTo : originalTo,
                GermanText.adults(counts), GermanText.children(counts), null, null, null, null, r.locations(),
                new ParsedMail.Period(originalFrom, originalTo)));
    }

    private static String blankQuotedLines(String text) {
        var m = QUOTED_LINE.matcher(text);
        var sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, " ".repeat(m.group().length()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static final Pattern RECEIPT = Pattern.compile("vielen\\s+Dank\\s+für\\s+Ihre\\s+E-Mail",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TICKET_ID = Pattern.compile("Ticket-ID:\\s*([A-Z]{3}-\\d{3}-\\d{5})\\b");
    /** A line quoted with „>“: a reply that quotes a receipt is no receipt itself. */
    private static final Pattern QUOTED_LINE = Pattern.compile("(?m)^\\s*>.*$");

    /** The ticket system's automatic receipt („vielen Dank für Ihre E-Mail … Ticket-ID: …“). */
    @Override
    public Optional<String> ticketReceipt(MailContent mail) {
        if (!fromAgency(mail)) {
            return Optional.empty();
        }
        var text = QUOTED_LINE.matcher(mail.text()).replaceAll("");
        if (!RECEIPT.matcher(text).find()) {
            return Optional.empty();
        }
        var id = TICKET_ID.matcher(text);
        return id.find() ? Optional.of(id.group(1)) : Optional.empty();
    }

    @Override
    public java.util.Set<String> agencyDomains() {
        return java.util.Set.of("fewoundmeer.de", "fewoscout.com");
    }

    private static boolean fromAgency(MailContent mail) {
        var sender = mail.sender() == null ? "" : mail.sender().toLowerCase(Locale.ROOT);
        // FeWoScout is Fewo & Meer's booking portal; its mails may carry no signature at all.
        // Forwarded mails come from the user: then the agency shows in the forwarded text.
        return sender.endsWith("fewoundmeer.de") || sender.endsWith("fewoscout.com")
                || mail.text().contains("Fewo & Meer")
                || mail.text().contains("fewoundmeer.de");
    }

    /**
     * Between a table label and its value: a tab or two spaces, with any further blanks around them.
     * Forwarding often leaves a space before or after the tab; a single space
     * stays too little, so prose lines starting with a label word are not read as fields.
     */
    private static final String SEPARATOR = "[ \\t\\u00a0]*(?:\\t|[ \\u00a0]{2})[ \\t\\u00a0]*";
    private static final Pattern ADDRESS = Pattern.compile("^Adresse" + SEPARATOR + "(.*?)\\s*$");

    /** "Label\tValue" lines from the HTML table. */
    private static String field(Reading r, String label, BookingField... fields) {
        return r.text(Pattern.compile("(?m)^" + Pattern.quote(label) + SEPARATOR + "(.*?)[ \\t\\u00a0]*$"), fields);
    }

    private static ParsedMail confirmation(String property, Reading r) {
        var guests = field(r, "Anzahl Gäste", BookingField.ADULTS, BookingField.CHILDREN);
        var rent = field(r, "Mietpreis", BookingField.EXPECTED_RENT);
        var phone = blankToNull(field(r, "Telefon", BookingField.GUEST_PHONE));
        String mobile = null;
        if (phone == null) {
            mobile = blankToNull(field(r, "Telefon (mobil)", BookingField.GUEST_PHONE));
        }
        var address = address(r);
        var guest = new Guest(field(r, "Name", BookingField.GUEST_NAME), address[0], address[1], address[2],
                address[3], phone != null ? phone : mobile, field(r, "Email", BookingField.GUEST_EMAIL));
        var ref = field(r, "Buchungsnummer", BookingField.EXTERNAL_REF);
        var arrival = field(r, "Anreise", BookingField.ARRIVAL);
        var departure = field(r, "Abreise", BookingField.DEPARTURE);
        return new ParsedMail(AGENCY, ParsedMail.MailType.CONFIRMATION, ref, property, date(arrival), date(departure),
                GermanText.adults(guests), GermanText.children(guests), null,
                rent == null ? null : amount(rent.replaceAll("[^0-9.,]", "")), guest).withLocations(r.locations());
    }

    /** Street, postal code, city and country from the multi-line address cell. */
    private static String[] address(Reading r) {
        var result = new String[4];
        var lines = r.text().split("\n", -1);
        var offset = 0;
        for (int i = 0; i < lines.length; offset += lines[i].length() + 1, i++) {
            var m = ADDRESS.matcher(lines[i]);
            if (!m.find()) {
                continue;
            }
            result[0] = blankToNull(r.value(Location.Part.TEXT, offset + m.start(1), offset + m.end(1),
                    BookingField.GUEST_STREET));
            var lineStart = offset + lines[i].length() + 1;
            for (int j = i + 1; j < lines.length && !lines[j].startsWith("Telefon") && !lines[j].startsWith("Email");
                    lineStart += lines[j].length() + 1, j++) {
                if (lines[j].isBlank()) {
                    continue;
                }
                var place = Pattern.compile("^(\\d{4,5})\\s+(.+)$").matcher(lines[j]);
                if (place.find()) {
                    result[1] = r.value(Location.Part.TEXT, lineStart + place.start(1), lineStart + place.end(1),
                            BookingField.GUEST_POSTAL_CODE);
                    result[2] = r.value(Location.Part.TEXT, lineStart + place.start(2), lineStart + place.end(2),
                            BookingField.GUEST_CITY);
                } else {
                    result[3] = r.value(Location.Part.TEXT, lineStart, lineStart + lines[j].length(),
                            BookingField.GUEST_COUNTRY);
                }
            }
            break;
        }
        return result;
    }

    private static ParsedMail shortNotice(java.util.regex.Matcher subject, Reading r) {
        var ref = r.group(Location.Part.SUBJECT, subject, 1, BookingField.EXTERNAL_REF);
        var property = r.group(Location.Part.SUBJECT, subject, 2, BookingField.PROPERTY_NAME);
        var from = r.group(Location.Part.SUBJECT, subject, 3, BookingField.ARRIVAL);
        var to = r.group(Location.Part.SUBJECT, subject, 4, BookingField.DEPARTURE);
        var persons = r.text(Pattern.compile("(?m)^Personenzahl:\\s*(.*)$"), BookingField.ADULTS,
                BookingField.CHILDREN);
        var pets = r.text(Pattern.compile("(?m)^Haustiere:\\s*(\\d+)"), BookingField.PETS);
        var phone = blankToNull(r.text(Pattern.compile("(?m)^Telefon-Nr\\.:[ \\t]*(.*)$"), BookingField.GUEST_PHONE));
        String mobile = null;
        if (phone == null) {
            mobile = blankToNull(r.text(Pattern.compile("(?m)^Mobil-Nr\\.:[ \\t]*(.*)$"), BookingField.GUEST_PHONE));
        }
        var name = r.text(Pattern.compile("(?m)^Gast-Name:\\s*(.*)$"), BookingField.GUEST_NAME);
        var guest = new Guest(name, null, null, null, null, phone != null ? phone : mobile, null);
        return new ParsedMail(AGENCY, ParsedMail.MailType.SHORT_NOTICE, ref, property, date(from), date(to),
                GermanText.adults(persons), GermanText.children(persons), pets == null ? null : Integer.valueOf(pets),
                null, guest).withLocations(r.locations());
    }
}
