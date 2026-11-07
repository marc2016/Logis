package app.logis.core.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls all enabled mailboxes one after another (design.md D4). */
@Component
@ConditionalOnProperty(name = "logis.mailimport.polling", havingValue = "true", matchIfMissing = true)
class MailPoller {

    private static final Logger log = LoggerFactory.getLogger(MailPoller.class);

    private final MailboxRepository mailboxes;
    private final MailService mails;

    MailPoller(MailboxRepository mailboxes, MailService mails) {
        this.mailboxes = mailboxes;
        this.mails = mails;
    }

    @Scheduled(initialDelayString = "${logis.mailimport.initial-delay:PT1M}",
            fixedDelayString = "${logis.mailimport.interval:PT5M}")
    void pollAll() {
        for (var mailbox : mailboxes.findByEnabledTrue()) {
            try {
                mails.poll(mailbox.getOwnerUserId());
            } catch (RuntimeException e) {
                log.warn("Polling mailbox of user {} failed", mailbox.getOwnerUserId(), e);
            }
        }
    }
}
