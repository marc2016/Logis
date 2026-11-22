package app.logis.core.pushover;

import app.logis.core.notifications.NotificationChannel;
import app.logis.core.pushover.PushoverClient.PushoverException;
import app.logis.core.users.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Notifications through Pushover. Sends the application token, the user's key, the title, the short text and the
 * link, and nothing else: no long text, no name or address of the user (spec pushover, „What is sent to Pushover“).
 */
@Component
@Order(20)
class PushoverChannel implements NotificationChannel {

    static final String ID = "pushover";

    private final PushoverStore store;
    private final PushoverClient client;

    PushoverChannel(PushoverStore store, PushoverClient client) {
        this.store = store;
        this.client = client;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Pushover";
    }

    @Override
    public Form form() {
        return Form.SHORT;
    }

    @Override
    public Optional<String> unusable(User user) {
        if (store.token().isEmpty()) {
            return Optional.of(PushoverService.NOT_SET_UP);
        }
        var key = store.userKey(user.getId());
        if (key.isEmpty()) {
            return Optional.of(PushoverService.NO_KEY);
        }
        return key.get().invalid() ? Optional.of(PushoverService.KEY_NO_LONGER_VALID) : Optional.empty();
    }

    @Override
    public void send(User user, Message message) {
        var token = store.token().orElseThrow();
        var key = store.userKey(user.getId()).orElseThrow();
        try {
            client.send(token, key.key(), message.title(), message.text(), message.link());
        } catch (PushoverException ex) {
            if (ex.kind() == PushoverException.Kind.INVALID_USER) {
                // Pushover asks not to repeat such requests: stop until the user stores a key again.
                store.markInvalid(user.getId());
            }
            throw ex;
        }
    }
}
