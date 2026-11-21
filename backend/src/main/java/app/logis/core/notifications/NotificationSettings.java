package app.logis.core.notifications;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * A user's switches per event type and channel (design D3 of notifications-pushover). Without a row a switch is
 * off, unless the type declares the channel as mandatory: then it is on, whatever is stored.
 */
@Component
class NotificationSettings {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    NotificationSettings(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The stored switches of the user: event type → channel → on. */
    Map<String, Map<String, Boolean>> stored(Long userId) {
        var result = new HashMap<String, Map<String, Boolean>>();
        jdbc.query("SELECT event_type, channel, enabled FROM core.notification_settings WHERE user_id = ?",
                rs -> {
                    result.computeIfAbsent(rs.getString(1), k -> new HashMap<>())
                            .put(rs.getString(2), rs.getBoolean(3));
                }, userId);
        return result;
    }

    boolean enabled(Long userId, NotificationType type, String channel) {
        if (type.mandatoryChannels().contains(channel)) {
            return true;
        }
        var rows = jdbc.queryForList("SELECT enabled FROM core.notification_settings "
                + "WHERE user_id = ? AND event_type = ? AND channel = ?", Boolean.class, userId, type.key(), channel);
        return !rows.isEmpty() && rows.get(0);
    }

    void set(Long userId, String typeKey, String channel, boolean enabled) {
        jdbc.update("INSERT INTO core.notification_settings (user_id, event_type, channel, enabled, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (user_id, event_type, channel) "
                        + "DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at",
                userId, typeKey, channel, enabled, Timestamp.from(clock.instant()));
    }
}
