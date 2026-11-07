package app.logis.modules.vacationrental;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static app.logis.modules.vacationrental.GermanText.amount;
import static app.logis.modules.vacationrental.GermanText.date;
import static app.logis.modules.vacationrental.GermanText.find;

/**
 * Rules for Holidu bookings delivered by DS Destination Solutions ("Buchungsmitteilung"). Only confirmations are
 * known so far; anything else is left to the AI fallback.
 */
@Component
@Order(2)
class HoliduDsParser implements MailParser {

    static final String AGENCY = "Holidu";

    private static final Pattern REF = Pattern.compile("Buchungsmitteilung \\(Buchungsnummer:\\s*([^)\\s]+)\\)");
    private static final Pattern PROPERTY = Pattern.compile("Ihre Unterkunft:\\s*(.+?)\\s*\\(Kategorie");
    private static final Pattern ARRIVAL = Pattern.compile("Anreise:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern DEPARTURE = Pattern.compile("Abreise:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern TRAVELLERS = Pattern.compile("(?m)^Reisende:\\s*(.+)$");
    private static final Pattern TOTAL = Pattern.compile("REISEPREIS:\\s*([\\d.,]+)\\s*EUR");
    private static final Pattern GUEST = Pattern.compile("(?m)^Gast:\\s*(.+?)\\s*(\\(K\\.-Nr\\..*)?$");
    private static final Pattern PHONE = Pattern.compile("(?m)^Tel\\.:\\s*(\\S+)");
    private static final Pattern EMAIL = Pattern.compile("(?m)^E-Mail:\\s*(\\S+)");
    private static final Pattern ADDRESS = Pattern.compile("^(.+?),\\s*(\\d{4,5})\\s+(.+?)(?:,\\s*(.+))?$");

    @Override
    public Optional<ParsedMail> parse(MailContent mail) {
        var text = mail.text();
        var sender = mail.sender() == null ? "" : mail.sender().toLowerCase(Locale.ROOT);
        var ref = find(REF, text);
        if (ref == null || !(sender.contains("ds-destinationsolutions") || text.contains("Holidu"))) {
            return Optional.empty();
        }
        // A confirmation says what was booked; cancellations and changes have other wording.
        if (!text.contains("wurde wie folgt gebucht")) {
            return Optional.empty();
        }
        var travellers = find(TRAVELLERS, text);
        return Optional.of(new ParsedMail(AGENCY, ParsedMail.MailType.CONFIRMATION, ref, find(PROPERTY, text),
                date(find(ARRIVAL, text)), date(find(DEPARTURE, text)), GermanText.adults(travellers),
                GermanText.children(travellers), null, amount(find(TOTAL, text)), guest(text)));
    }

    private static Guest guest(String text) {
        String name = null;
        String[] address = new String[4];
        var lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            var m = GUEST.matcher(lines[i]);
            if (m.find()) {
                name = m.group(1);
                if (i + 1 < lines.length) {
                    var a = ADDRESS.matcher(lines[i + 1]);
                    if (a.find()) {
                        address = new String[] {a.group(1), a.group(2), a.group(3), a.group(4)};
                    }
                }
                break;
            }
        }
        return new Guest(name, address[0], address[1], address[2], address[3], find(PHONE, text),
                find(EMAIL, text));
    }
}
