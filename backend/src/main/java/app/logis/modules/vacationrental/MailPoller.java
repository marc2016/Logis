package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls all enabled mailboxes one after another (design.md D4). */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
@ConditionalOnProperty(name = "logis.mailimport.polling", havingValue = "true", matchIfMissing = true)
class MailPoller {

    private static final Logger log = LoggerFactory.getLogger(MailPoller.class);

    private final MailboxRepository mailboxes;
    private final MailImportService imports;

    MailPoller(MailboxRepository mailboxes, MailImportService imports) {
        this.mailboxes = mailboxes;
        this.imports = imports;
    }

    @Scheduled(initialDelayString = "${logis.mailimport.initial-delay:PT1M}",
            fixedDelayString = "${logis.mailimport.interval:PT5M}")
    void pollAll() {
        for (var mailbox : mailboxes.findByEnabledTrue()) {
            try {
                imports.poll(mailbox.getOwnerUserId());
            } catch (RuntimeException e) {
                log.warn("Polling mailbox of user {} failed", mailbox.getOwnerUserId(), e);
            }
        }
    }
}
