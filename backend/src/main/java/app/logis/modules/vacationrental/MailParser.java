package app.logis.modules.vacationrental;

import app.logis.core.mail.MailContent;

import java.util.Optional;
import java.util.Set;

/** Reads a booking mail; empty if the parser does not recognise it. */
interface MailParser {

    Optional<ParsedMail> parse(MailContent mail);

    /**
     * Sender domains of the agency this parser reads, in lower case; addresses there stay in the admin export
     * (design D4 of mail-export-pseudonymization).
     */
    default Set<String> agencyDomains() {
        return Set.of();
    }
}
