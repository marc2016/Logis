package app.logis.core.notifications;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** The registered event types and channels, in their order. */
@Component
class NotificationTypes {

    private static final Pattern KEY = Pattern.compile("^[a-z]+\\.[A-Za-z0-9]+$");

    private final Map<String, NotificationType> types = new LinkedHashMap<>();
    private final Map<String, NotificationChannel> channels = new LinkedHashMap<>();

    NotificationTypes(List<NotificationType> types, List<NotificationChannel> channels) {
        for (var channel : channels) {
            if (this.channels.putIfAbsent(channel.id(), channel) != null) {
                throw new IllegalStateException("Notification channel registered twice: " + channel.id());
            }
        }
        for (var type : types) {
            if (!KEY.matcher(type.key()).matches()) {
                throw new IllegalStateException("Invalid notification type key: " + type.key());
            }
            if (this.types.putIfAbsent(type.key(), type) != null) {
                throw new IllegalStateException("Notification type registered twice: " + type.key());
            }
            for (var mandatory : type.mandatoryChannels()) {
                if (!this.channels.containsKey(mandatory)) {
                    throw new IllegalStateException("Unknown mandatory channel " + mandatory + " of " + type.key());
                }
            }
        }
    }

    Optional<NotificationType> type(String key) {
        return Optional.ofNullable(types.get(key));
    }

    Optional<NotificationChannel> channel(String id) {
        return Optional.ofNullable(channels.get(id));
    }

    Collection<NotificationType> types() {
        return types.values();
    }

    Collection<NotificationChannel> channels() {
        return channels.values();
    }
}
