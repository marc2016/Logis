package app.logis.modules.vacationrental;

import java.util.Optional;

/** Reads a mail with an AI model; empty if the model gave no usable answer. */
interface AiExtractor {

    Optional<AiExtraction> extract(String sender, String subject, String text);
}
