/**
 * Mail inbox of the core: an IMAP mailbox per user, polling, stored mails and the inbox. Parts of Logis handle mails
 * through {@link MailHandler}; public API: {@link MailHandler}, {@link Handled}, {@link MailContent}, {@link Mails}.
 */
@NamedInterface("mail")
package app.logis.core.mail;

import org.springframework.modulith.NamedInterface;
