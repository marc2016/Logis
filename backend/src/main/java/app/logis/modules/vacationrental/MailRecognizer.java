package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
                .flatMap(MailRecognizer::toParsed)
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
        var guest = new Guest(text(e.guestName()), text(e.guestStreet()), text(e.guestPostalCode()),
                text(e.guestCity()), text(e.guestCountry()), text(e.guestPhone()), text(e.guestEmail()));
        return Optional.of(new ParsedMail(text(e.agency()), type, text(e.bookingRef()), text(e.propertyName()),
                isoDate(e.arrival()), isoDate(e.departure()), number(e.adults()), number(e.children()),
                number(e.pets()), money(e.expectedRent()), guest.isEmpty() ? null : guest));
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
