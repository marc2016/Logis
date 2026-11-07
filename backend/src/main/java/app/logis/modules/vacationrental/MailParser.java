package app.logis.modules.vacationrental;

import java.util.Optional;

/** Reads a booking mail; empty if the parser does not recognise it. */
interface MailParser {

    Optional<ParsedMail> parse(MailContent mail);
}
