package app.logis.core.notifications;

import java.util.Collection;
import java.util.Map;

/**
 * Tells users about an event. Callers name the users who may see the record the event is about; the core leaves out
 * disabled users and applies each user's switches. Sending happens in the background once the caller's transaction
 * is committed, and never throws.
 */
public interface Notifications {

    /** The same notification for all given users. */
    void send(String typeKey, Collection<Long> userIds, Notification notification);

    /** A notification of its own per user, e.g. a digest of the user's units. */
    void send(String typeKey, Map<Long, Notification> perUser);
}
