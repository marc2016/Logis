package app.logis.core.mail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Polling of mailboxes runs on the scheduler. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class MailConfiguration {
}
