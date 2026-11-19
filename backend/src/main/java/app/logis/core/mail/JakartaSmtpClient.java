package app.logis.core.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Builds a mail sender per call from the stored settings, so a new configuration applies at once. */
@Component
class JakartaSmtpClient implements SmtpClient {

    private static final String CONNECT_TIMEOUT = "10000";
    private static final String READ_TIMEOUT = "10000";
    private static final String WRITE_TIMEOUT = "20000";

    @Override
    public void test(Config config) {
        try {
            sender(config).testConnection();
        } catch (MessagingException | MailException ex) {
            throw new SmtpException(ex);
        }
    }

    @Override
    public void send(Config config, Mail mail) {
        try {
            var sender = sender(config);
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(mail.fromAddress(), mail.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), false);
            sender.send(message);
        } catch (MessagingException | MailException | UnsupportedEncodingException ex) {
            throw new SmtpException(ex);
        }
    }

    private static JavaMailSenderImpl sender(Config config) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        var auth = config.username() != null && !config.username().isBlank();
        if (auth) {
            sender.setUsername(config.username());
            sender.setPassword(config.password());
        }
        var props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.smtp.auth", String.valueOf(auth));
        props.setProperty("mail.smtp.connectiontimeout", CONNECT_TIMEOUT);
        props.setProperty("mail.smtp.timeout", READ_TIMEOUT);
        props.setProperty("mail.smtp.writetimeout", WRITE_TIMEOUT);
        switch (config.security()) {
            case STARTTLS -> {
                props.setProperty("mail.smtp.starttls.enable", "true");
                props.setProperty("mail.smtp.starttls.required", "true");
            }
            case SSL_TLS -> props.setProperty("mail.smtp.ssl.enable", "true");
            case NONE -> {
            }
        }
        // Certificates are checked against the JVM trust store; there is no option to skip that.
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        sender.setJavaMailProperties(props);
        return sender;
    }
}
