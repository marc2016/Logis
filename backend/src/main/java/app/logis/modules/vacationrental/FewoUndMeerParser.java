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
import static app.logis.modules.vacationrental.GermanText.find;

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

    @Override
    public Optional<ParsedMail> parse(MailContent mail) {
        if (!fromAgency(mail)) {
            return Optional.empty();
        }
        var subject = mail.businessSubject();
        var m = CONFIRMATION_SUBJECT.matcher(subject);
        if (m.find()) {
            return Optional.of(confirmation(m.group(1).strip(), mail.text()));
        }
        m = SHORT_NOTICE_SUBJECT.matcher(subject);
        if (m.find()) {
            return Optional.of(shortNotice(m.group(1), m.group(2).strip(), m.group(3), m.group(4), mail.text()));
        }
        m = CANCELLATION_SUBJECT.matcher(subject);
        if (m.find()) {
            return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.CANCELLATION, m.group(1),
                    m.group(2).strip(), date(m.group(3)), date(m.group(4)), null, null, null, null, null));
        }
        m = BLOCK_NOTICE_SUBJECT.matcher(subject);
        if (m.find()) {
            return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.BLOCK_NOTICE, m.group(2),
                    m.group(1).strip(), date(m.group(3)), date(m.group(4)), null, null, null, null, null,
                    blankToNull(m.group(5))));
        }
        return Optional.empty();
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
    private static String field(String label, String text) {
        return find(Pattern.compile("(?m)^" + Pattern.quote(label) + SEPARATOR + "(.*?)[ \\t\\u00a0]*$"), text);
    }

    private static ParsedMail confirmation(String property, String text) {
        var guests = field("Anzahl Gäste", text);
        var rent = field("Mietpreis", text);
        var phone = blankToNull(field("Telefon", text));
        var mobile = blankToNull(field("Telefon (mobil)", text));
        var address = address(text);
        var guest = new Guest(field("Name", text), address[0], address[1], address[2], address[3],
                phone != null ? phone : mobile, field("Email", text));
        return new ParsedMail(AGENCY, ParsedMail.MailType.CONFIRMATION, field("Buchungsnummer", text), property,
                date(field("Anreise", text)), date(field("Abreise", text)), GermanText.adults(guests),
                GermanText.children(guests), null,
                rent == null ? null : amount(rent.replaceAll("[^0-9.,]", "")), guest);
    }

    /** Street, postal code, city and country from the multi-line address cell. */
    private static String[] address(String text) {
        var result = new String[4];
        var lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            var m = ADDRESS.matcher(lines[i]);
            if (!m.find()) {
                continue;
            }
            result[0] = blankToNull(m.group(1));
            for (int j = i + 1; j < lines.length && !lines[j].startsWith("Telefon") && !lines[j].startsWith("Email");
                    j++) {
                if (lines[j].isBlank()) {
                    continue;
                }
                var place = Pattern.compile("^(\\d{4,5})\\s+(.+)$").matcher(lines[j]);
                if (place.find()) {
                    result[1] = place.group(1);
                    result[2] = place.group(2).strip();
                } else {
                    result[3] = lines[j].strip();
                }
            }
            break;
        }
        return result;
    }

    private static ParsedMail shortNotice(String ref, String property, String from, String to, String text) {
        var persons = find(Pattern.compile("(?m)^Personenzahl:\\s*(.*)$"), text);
        var pets = find(Pattern.compile("(?m)^Haustiere:\\s*(\\d+)"), text);
        var phone = blankToNull(find(Pattern.compile("(?m)^Telefon-Nr\\.:[ \\t]*(.*)$"), text));
        var mobile = blankToNull(find(Pattern.compile("(?m)^Mobil-Nr\\.:[ \\t]*(.*)$"), text));
        var name = find(Pattern.compile("(?m)^Gast-Name:\\s*(.*)$"), text);
        var guest = new Guest(name, null, null, null, null, phone != null ? phone : mobile, null);
        return new ParsedMail(AGENCY, ParsedMail.MailType.SHORT_NOTICE, ref, property, date(from), date(to),
                GermanText.adults(persons), GermanText.children(persons), pets == null ? null : Integer.valueOf(pets),
                null, guest);
    }
}
