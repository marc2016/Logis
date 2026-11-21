package app.logis.core.notifications;

import java.util.Set;

/**
 * A kind of event users can be notified about, registered as a bean. Beans of a disabled module do not exist, so
 * their types are neither offered nor sent; the users' switches for them stay stored.
 */
public interface NotificationType {

    /** Stable key made of a module part and a name, e.g. {@code vacationrental.bookingReview}. */
    String key();

    /** German label of the row in „Benachrichtigungen“. */
    String label();

    /** German label of the group the row is listed under, e.g. „Ferienvermietung“. */
    String group();

    /** IDs of channels that are always on for this type and cannot be switched off. */
    default Set<String> mandatoryChannels() {
        return Set.of();
    }

    static NotificationType of(String key, String label, String group) {
        return of(key, label, group, Set.of());
    }

    static NotificationType of(String key, String label, String group, Set<String> mandatoryChannels) {
        return new NotificationType() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String group() {
                return group;
            }

            @Override
            public Set<String> mandatoryChannels() {
                return mandatoryChannels;
            }
        };
    }
}
