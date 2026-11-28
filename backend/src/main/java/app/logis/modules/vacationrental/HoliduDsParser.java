package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static app.logis.modules.vacationrental.GermanText.amount;
import static app.logis.modules.vacationrental.GermanText.date;
import static app.logis.modules.vacationrental.GermanText.find;

/**
 * Rules for bookings delivered by DS Destination Solutions ("Buchungsmitteilung"), e.g. from Holidu or VVV
 * Talstadt. The agency is the intermediary the mail names. Only confirmations are known so far; anything else is
 * left to the AI fallback.
 */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
@Order(2)
class HoliduDsParser implements MailParser {

    static final String AGENCY = "Holidu";

    private static final int AGENCY_MAX_LENGTH = 64;

    private static final Pattern INTERMEDIARY = Pattern.compile("(?m)^Vermittler \\(Stornierung/Änderung\\):[ \\t]*(.+)$");
    private static final Pattern COOPERATION_SUFFIX = Pattern.compile("\\s+in Kooperation mit\\b.*$");
    private static final Pattern BOOKED = Pattern.compile("wurde wie folgt\\s+gebucht");
    private static final Pattern REF =Pattern.compile("Buchungsmitteilung \\(Buchungsnummer:\\s*([^)\\s]+)\\)");
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
    public java.util.Set<String> agencyDomains() {
        return java.util.Set.of("ds-destinationsolutions.com");
    }

    @Override
    public Optional<ParsedMail> parse(MailContent mail) {
        var text = mail.text();
        var sender = mail.sender() == null ? "" : mail.sender().toLowerCase(Locale.ROOT);
        var ref = find(REF, text);
        var intermediary = find(INTERMEDIARY, text);
        if (ref == null || !(sender.contains("ds-destinationsolutions") || text.contains("Holidu")
                || intermediary != null)) {
            return Optional.empty();
        }
        // A confirmation says what was booked; cancellations and changes have other wording.
        if (!BOOKED.matcher(text).find()) {
            return Optional.empty();
        }
        var r = new Reading(mail.businessSubject(), text);
        r.text(REF, BookingField.EXTERNAL_REF);
        var travellers = r.text(TRAVELLERS, BookingField.ADULTS, BookingField.CHILDREN);
        var property = r.text(PROPERTY, BookingField.PROPERTY_NAME);
        var arrival = date(r.text(ARRIVAL, BookingField.ARRIVAL));
        var departure = date(r.text(DEPARTURE, BookingField.DEPARTURE));
        var total = amount(r.text(TOTAL, BookingField.EXPECTED_RENT));
        var guest = guest(r);
        return Optional.of(new ParsedMail(agency(intermediary), ParsedMail.MailType.CONFIRMATION, ref, property,
                arrival, departure, GermanText.adults(travellers), GermanText.children(travellers), null, total, guest)
                .withLocations(r.locations()));
    }

    /**
     * "Holidu" for Holidu or when the mail names no intermediary, so existing Holidu bookings keep matching;
     * otherwise the intermediary's name without "in Kooperation mit …".
     */
    static String agency(String intermediary) {
        if (intermediary == null || intermediary.toLowerCase(Locale.ROOT).contains("holidu")) {
            return AGENCY;
        }
        var name = COOPERATION_SUFFIX.matcher(intermediary).replaceFirst("").strip();
        if (name.isEmpty()) {
            return AGENCY;
        }
        return name.length() > AGENCY_MAX_LENGTH ? name.substring(0, AGENCY_MAX_LENGTH).strip() : name;
    }

    private static Guest guest(Reading r) {
        String name = null;
        String[] address = new String[4];
        var lines = r.text().split("\n", -1);
        var offset = 0;
        for (int i = 0; i < lines.length; offset += lines[i].length() + 1, i++) {
            var m = GUEST.matcher(lines[i]);
            if (m.find()) {
                name = r.value(Location.Part.TEXT, offset + m.start(1), offset + m.end(1), BookingField.GUEST_NAME);
                if (i + 1 < lines.length) {
                    var next = offset + lines[i].length() + 1;
                    var a = ADDRESS.matcher(lines[i + 1]);
                    if (a.find()) {
                        address = new String[] {part(r, a, 1, next, BookingField.GUEST_STREET),
                                part(r, a, 2, next, BookingField.GUEST_POSTAL_CODE),
                                part(r, a, 3, next, BookingField.GUEST_CITY),
                                part(r, a, 4, next, BookingField.GUEST_COUNTRY)};
                    }
                }
                break;
            }
        }
        return new Guest(name, address[0], address[1], address[2], address[3],
                r.text(PHONE, BookingField.GUEST_PHONE), r.text(EMAIL, BookingField.GUEST_EMAIL));
    }

    /** A group of a match on the line starting at {@code lineStart}, located in the whole text. */
    private static String part(Reading r, java.util.regex.Matcher m, int group, int lineStart, BookingField field) {
        return m.group(group) == null ? null
                : r.value(Location.Part.TEXT, lineStart + m.start(group), lineStart + m.end(group), field);
    }
}
