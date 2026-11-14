package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Rules first; only mails no rule recognises go to the AI, if it is enabled. */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class MailRecognizer {

    enum Source {
        RULE, AI, NONE
    }

    record Recognition(ParsedMail parsed, Source source) {

        static final Recognition NONE = new Recognition(null, Source.NONE);
    }

    private final List<MailParser> rules;
    private final ObjectProvider<AiExtractor> ai;

    MailRecognizer(List<MailParser> rules, ObjectProvider<AiExtractor> ai) {
        this.rules = rules;
        this.ai = ai;
    }

    boolean aiEnabled() {
        return ai.getIfAvailable() != null;
    }

    /** Only the fixed rules, never the AI; for deriving sources of mails imported earlier. */
    Optional<ParsedMail> recognizeByRules(MailContent mail) {
        for (var rule : rules) {
            var parsed = rule.parse(mail).filter(ParsedMail::usable);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    Recognition recognize(MailContent mail) {
        for (var rule : rules) {
            var parsed = rule.parse(mail).filter(ParsedMail::usable);
            if (parsed.isPresent()) {
                return new Recognition(parsed.get(), Source.RULE);
            }
        }
        var extractor = ai.getIfAvailable();
        if (extractor == null) {
            return Recognition.NONE;
        }
        return extractor.extract(mail.sender(), mail.subject(), mail.text())
                .flatMap(e -> toParsed(e).map(p -> p.withLocations(locate(e, mail))))
                .filter(ParsedMail::usable)
                .map(p -> new Recognition(p, Source.AI))
                .orElse(Recognition.NONE);
    }

    static Optional<ParsedMail> toParsed(AiExtraction e) {
        ParsedMail.MailType type;
        try {
            type = ParsedMail.MailType.valueOf(text(e.type()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
        if (type == ParsedMail.MailType.BLOCK_NOTICE) {
            // Only the rules know block notices; the AI is not asked for them.
            return Optional.empty();
        }
        var guest = new Guest(text(e.guestName()), text(e.guestStreet()), text(e.guestPostalCode()),
                text(e.guestCity()), text(e.guestCountry()), text(e.guestPhone()), text(e.guestEmail()));
        return Optional.of(new ParsedMail(text(e.agency()), type, text(e.bookingRef()), text(e.propertyName()),
                isoDate(e.arrival()), isoDate(e.departure()), number(e.adults()), number(e.children()),
                number(e.pets()), money(e.expectedRent()), guest.isEmpty() ? null : guest));
    }

    /** AI field names that differ from the booking field keys. */
    private static final Map<String, BookingField> AI_FIELDS = Map.of("bookingRef", BookingField.EXTERNAL_REF);

    /**
     * Locations of the passages the AI quoted (design.md D5): first in the text, then in the subject. A passage that
     * is not found leaves its value without a location.
     */
    static Map<BookingField, Location> locate(AiExtraction e, MailContent mail) {
        var result = new EnumMap<BookingField, Location>(BookingField.class);
        if (e.quotes() == null) {
            return result;
        }
        for (var quote : e.quotes()) {
            if (quote == null || quote.field() == null) {
                continue;
            }
            var field = Optional.ofNullable(AI_FIELDS.get(quote.field().strip()))
                    .or(() -> BookingField.ofKey(quote.field().strip()));
            if (field.isEmpty() || result.containsKey(field.get())) {
                continue;
            }
            find(quote.passage(), mail.text(), Location.Part.TEXT)
                    .or(() -> find(quote.passage(), mail.businessSubject(), Location.Part.SUBJECT))
                    .ifPresent(at -> result.put(field.get(), at));
        }
        return result;
    }

    /** The first occurrence of the passage, with any run of blanks matching any other run of blanks. */
    static Optional<Location> find(String passage, String source, Location.Part part) {
        if (passage == null || passage.isBlank() || source == null) {
            return Optional.empty();
        }
        var tokens = passage.strip().split("[\\s\\u00a0]+");
        var regex = Arrays.stream(tokens).map(Pattern::quote).collect(Collectors.joining("[\\s\\u00a0]+"));
        var m = Pattern.compile(regex).matcher(source);
        return m.find() ? Optional.of(new Location(part, m.start(), m.end())) : Optional.empty();
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static LocalDate isoDate(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer number(String s) {
        try {
            var n = s == null || s.isBlank() ? null : Integer.valueOf(s.strip());
            return n == null || n < 0 || n > 99 ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal money(String s) {
        try {
            var v = s == null || s.isBlank() ? null : new BigDecimal(s.strip()).setScale(2, java.math.RoundingMode.HALF_UP);
            return v == null || v.signum() < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
