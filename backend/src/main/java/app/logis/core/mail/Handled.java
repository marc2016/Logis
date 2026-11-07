package app.logis.core.mail;

/**
 * Outcome a handler reports for a mail.
 *
 * @param result      a code shown in the inbox, e.g. {@code BOOKING_CREATED}; the frontend module labels it
 * @param kind        what kind of mail it is, e.g. {@code CONFIRMATION}
 * @param source      who sent it in business terms, e.g. the agency "Fewo & Meer"
 * @param reference   the sender's reference, e.g. a booking number
 * @param subjectName what the mail is about, e.g. the property name as the agency writes it
 * @param linkType    type of the record the mail created or changed, e.g. {@code vacationrental.booking}
 * @param data        handler data stored with the mail as JSON (for display and debugging)
 * @param parsedBy    {@code RULE} or {@code AI}
 */
public record Handled(String result, String detail, String kind, String source, String reference,
        String subjectName, String linkType, Long linkId, Object data, String parsedBy) {
}
