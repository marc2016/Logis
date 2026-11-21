package app.logis.core.mail;

/**
 * A mail the regular poll just fetched was stored and offered to the handlers. Not published for older mails the
 * user fetches, for mails processed again, or for mails received more than two days before: only what is new may
 * cause notifications (design D6 of notifications-pushover). Part of the public API of the core.
 *
 * @param handlerKey key of the handler that took the mail; null if none did
 * @param result     the result code stored with the mail
 * @param linkType   type of the record the mail created or changed; null for none
 */
public record MailImported(Long ownerUserId, Long mailId, String handlerKey, String result, String linkType,
        Long linkId) {
}
