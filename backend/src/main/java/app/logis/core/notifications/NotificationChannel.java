package app.logis.core.notifications;

import app.logis.core.users.User;

import java.util.Optional;

/**
 * A way to reach a user, registered as a bean; {@code @Order} gives the order of the columns. A new channel needs
 * no change to the event types: it only chooses the form it sends.
 */
public interface NotificationChannel {

    enum Form {
        /** Title and short text: for services outside the system. */
        SHORT,
        /** Title and long text. */
        LONG
    }

    /** Stable ID as stored with the switches, e.g. {@code mail}. */
    String id();

    /** German label of the column, e.g. „E-Mail“. */
    String label();

    Form form();

    /** Empty if the channel can reach the user; otherwise a German hint that says what is missing. */
    Optional<String> unusable(User user);

    /** Sends to a user the channel is usable for; throws if sending failed. */
    void send(User user, Message message);

    /**
     * @param text the short or the long text, as the channel's form asks
     * @param link full address of the page in the app; null if there is none or no public address is configured
     */
    record Message(String title, String text, String link) {
    }
}
