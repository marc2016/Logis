package app.logis.core.notifications;

import app.logis.core.users.User;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * For tests: a channel that records what it is asked to send, one that always fails, and two event types. Import
 * it to see notifications without a mail server or Pushover.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestChannels {

    public static final String RECORDING = "recording";
    public static final String RECORDING_LONG = "recordinglong";
    public static final String BROKEN = "broken";
    public static final String TYPE = "core.testEvent";
    public static final String MANDATORY_TYPE = "core.testMandatory";

    public record Sent(Long userId, String title, String text, String link) {
    }

    /** Takes the short form, like a channel that leaves the system. */
    public static class Recording implements NotificationChannel {

        private final List<Sent> sent = new CopyOnWriteArrayList<>();

        @Override
        public String id() {
            return RECORDING;
        }

        @Override
        public String label() {
            return "Aufzeichnung";
        }

        @Override
        public Form form() {
            return Form.SHORT;
        }

        /** Forgets what was sent so far. */
        public void clear() {
            sent.clear();
        }

        @Override
        public Optional<String> unusable(User user) {
            return Optional.empty();
        }

        @Override
        public void send(User user, Message message) {
            sent.add(new Sent(user.getId(), message.title(), message.text(), message.link()));
        }

        public List<Sent> sent() {
            return List.copyOf(sent);
        }

        public List<Sent> sentTo(Long userId) {
            return sent.stream().filter(s -> s.userId().equals(userId)).toList();
        }

        /** Waits until the user got {@code count} notifications; for 0 waits a moment and expects none. */
        public List<Sent> await(Long userId, int count) throws InterruptedException {
            var deadline = System.currentTimeMillis() + (count == 0 ? 700 : 5000);
            while (System.currentTimeMillis() < deadline && (count == 0 || sentTo(userId).size() < count)) {
                Thread.sleep(25);
            }
            var result = sentTo(userId);
            if (result.size() != count) {
                throw new AssertionError("Expected " + count + " notifications for user " + userId + " but got "
                        + result);
            }
            return result;
        }
    }

    @Bean
    @Order(1)
    NotificationChannel brokenChannel() {
        return new NotificationChannel() {
            @Override
            public String id() {
                return BROKEN;
            }

            @Override
            public String label() {
                return "Kaputt";
            }

            @Override
            public Form form() {
                return Form.LONG;
            }

            @Override
            public Optional<String> unusable(User user) {
                return Optional.empty();
            }

            @Override
            public void send(User user, Message message) {
                throw new IllegalStateException("down: " + message.text());
            }
        };
    }

    /** Takes the long form, like the user's own mailbox. */
    public static class LongRecording extends Recording {

        @Override
        public String id() {
            return RECORDING_LONG;
        }

        @Override
        public String label() {
            return "Aufzeichnung lang";
        }

        @Override
        public Form form() {
            return Form.LONG;
        }
    }

    @Bean
    @Order(2)
    @org.springframework.context.annotation.Primary
    Recording recordingChannel() {
        return new Recording();
    }

    @Bean
    @Order(3)
    LongRecording longRecordingChannel() {
        return new LongRecording();
    }

    /** Switches both recording channels on for the user. */
    public static void switchOn(org.springframework.jdbc.core.JdbcTemplate jdbc, Long userId, String... types) {
        for (var type : types) {
            for (var channel : new String[] {RECORDING, RECORDING_LONG}) {
                jdbc.update("INSERT INTO core.notification_settings (user_id, event_type, channel, enabled, "
                        + "updated_at) VALUES (?, ?, ?, true, now()) ON CONFLICT DO NOTHING", userId, type, channel);
            }
        }
    }

    @Bean
    NotificationType testEventType() {
        return NotificationType.of(TYPE, "Testereignis", "Test");
    }

    @Bean
    NotificationType testMandatoryType() {
        return NotificationType.of(MANDATORY_TYPE, "Pflichtereignis", "Test", Set.of(RECORDING));
    }
}
