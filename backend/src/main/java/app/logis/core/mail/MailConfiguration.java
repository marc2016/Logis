package app.logis.core.mail;

import app.logis.core.notifications.NotificationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Polling of mailboxes runs on the scheduler. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class MailConfiguration {

    @Bean
    NotificationType mailAttentionNotificationType() {
        return MailService.ATTENTION;
    }

    @Bean
    NotificationType mailFetchFailedNotificationType() {
        return MailService.FETCH_FAILED;
    }
}
