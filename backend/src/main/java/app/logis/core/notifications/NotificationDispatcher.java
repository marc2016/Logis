package app.logis.core.notifications;

import app.logis.core.users.User;
import app.logis.core.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends notifications in the background after the caller's transaction is committed (design D4 of
 * notifications-pushover). Switches, the user's state and whether a channel is usable are read when sending. A
 * failing channel or recipient never stops the others and never reaches the caller.
 */
@Service
class NotificationDispatcher implements Notifications {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationTypes registry;
    private final NotificationSettings settings;
    private final UserRepository users;
    private final TaskExecutor executor;
    private final String publicUrl;

    NotificationDispatcher(NotificationTypes registry, NotificationSettings settings, UserRepository users,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor,
            @Value("${logis.auth.public-url:}") String publicUrl) {
        this.registry = registry;
        this.settings = settings;
        this.users = users;
        this.executor = executor;
        var trimmed = publicUrl == null ? "" : publicUrl.trim();
        this.publicUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public void send(String typeKey, Collection<Long> userIds, Notification notification) {
        var perUser = new LinkedHashMap<Long, Notification>();
        userIds.forEach(id -> perUser.put(id, notification));
        send(typeKey, perUser);
    }

    @Override
    public void send(String typeKey, Map<Long, Notification> perUser) {
        var type = registry.type(typeKey).orElse(null);
        if (type == null) {
            // E.g. raised for a module that is switched off: nothing to offer, nothing to send.
            log.debug("Notification of unknown type {} dropped", typeKey);
            return;
        }
        if (perUser.isEmpty()) {
            return;
        }
        var copy = Map.copyOf(perUser);
        afterCommit(() -> copy.forEach((userId, notification) -> deliver(type, userId, notification)));
    }

    private void afterCommit(Runnable task) {
        Runnable background = () -> {
            try {
                executor.execute(task);
            } catch (RuntimeException ex) {
                log.warn("Notifications could not be handed to the background: {}", ex.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    background.run();
                }
            });
        } else {
            background.run();
        }
    }

    private void deliver(NotificationType type, Long userId, Notification notification) {
        User user;
        try {
            user = users.findById(userId).filter(User::isEnabled).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Notification {} for user {} dropped: {}", type.key(), userId, ex.getClass().getSimpleName());
            return;
        }
        if (user == null) {
            return;
        }
        var link = notification.path() == null || publicUrl.isEmpty() ? null : publicUrl + notification.path();
        for (var channel : registry.channels()) {
            try {
                if (!settings.enabled(userId, type, channel.id()) || channel.unusable(user).isPresent()) {
                    continue;
                }
                var text = channel.form() == NotificationChannel.Form.SHORT ? notification.shortText()
                        : notification.longText();
                channel.send(user, new NotificationChannel.Message(notification.title(), text, link));
            } catch (RuntimeException ex) {
                // Never the text: only what should have gone where, and why it failed.
                log.warn("Notification {} could not be sent through {} for user {}: {}", type.key(), channel.id(),
                        userId, ex.getClass().getSimpleName());
            }
        }
    }
}
