package app.logis.core.notifications;

import app.logis.core.mail.MailSender;
import app.logis.core.users.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Notifications by mail to the user's own address, through the admin's outgoing mail server. */
@Component
@Order(10)
class MailChannel implements NotificationChannel {

    static final String ID = "mail";
    static final String NOT_SET_UP = "Der E-Mail-Versand ist nicht eingerichtet";
    static final String NO_ADDRESS = "Keine E-Mail-Adresse hinterlegt";

    private final MailSender mail;

    MailChannel(MailSender mail) {
        this.mail = mail;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "E-Mail";
    }

    @Override
    public Form form() {
        return Form.LONG;
    }

    @Override
    public Optional<String> unusable(User user) {
        if (user.getEmail() == null) {
            return Optional.of(NO_ADDRESS);
        }
        // Checked before every send, so no connection is tried without a server.
        return mail.configured() ? Optional.empty() : Optional.of(NOT_SET_UP);
    }

    @Override
    public void send(User user, Message message) {
        var text = message.link() == null ? message.text()
                : message.text().stripTrailing() + "\n\nIn Logis öffnen: " + message.link() + "\n";
        mail.send(user.getEmail(), message.title(), text);
    }
}
