package app.logis.core.notifications;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The registry of event types (spec notifications, „Event types“). */
class NotificationTypesTest {

    @Test
    void keepsTheOrderOfRegistration() {
        var registry = new NotificationTypes(List.of(NotificationType.of("core.b", "B", "Kern"),
                NotificationType.of("core.a", "A", "Kern")), List.of());
        assertThat(registry.types()).extracting(NotificationType::key).containsExactly("core.b", "core.a");
        assertThat(registry.type("core.a")).isPresent();
        assertThat(registry.type("core.c")).isEmpty();
    }

    @Test
    void rejectsTwoTypesWithTheSameKey() {
        assertThatThrownBy(() -> new NotificationTypes(List.of(NotificationType.of("core.a", "A", "Kern"),
                NotificationType.of("core.a", "Other", "Kern")), List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("core.a");
    }

    @Test
    void rejectsKeysWithoutAModulePartAndUnknownMandatoryChannels() {
        assertThatThrownBy(() -> new NotificationTypes(List.of(NotificationType.of("signIn", "A", "Kern")), List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new NotificationTypes(
                List.of(NotificationType.of("core.a", "A", "Kern", Set.of("carrier-pigeon"))), List.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("carrier-pigeon");
    }
}
