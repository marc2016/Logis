package app.logis.core.mail;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

/** IMAP via Jakarta Mail; the folder is opened read-only and bodies are read with PEEK, so nothing changes. */
@Component
class JakartaImapClient implements ImapClient {

    private static final int TIMEOUT_MS = 20_000;

    private final Clock clock;

    JakartaImapClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void test(Config config) {
        try (var store = connect(config)) {
            var folder = store.getFolder(config.folder());
            folder.open(Folder.READ_ONLY);
            folder.close(false);
        } catch (MessagingException e) {
            throw new ImapException(e.getMessage(), e);
        }
    }

    @Override
    public List<FetchedMail> fetchSince(Config config, LocalDate since, Predicate<String> known) {
        var result = new ArrayList<FetchedMail>();
        var sinceInstant = since.atStartOfDay(clock.getZone()).toInstant();
        try (var store = connect(config)) {
            var folder = store.getFolder(config.folder());
            folder.open(Folder.READ_ONLY);
            try {
                var messages = folder.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(sinceInstant)));
                for (Message message : messages) {
                    var received = message.getReceivedDate() != null ? message.getReceivedDate()
                            : message.getSentDate();
                    if (received != null && received.toInstant().isBefore(sinceInstant)) {
                        continue;
                    }
                    var ids = message.getHeader("Message-ID");
                    var raw = new ByteArrayOutputStream();
                    var messageId = ids != null && ids.length > 0 ? ids[0].trim() : null;
                    if (messageId != null && known.test(messageId)) {
                        continue;
                    }
                    message.writeTo(raw);
                    if (messageId == null) {
                        // Without a Message-ID the content identifies the mail.
                        messageId = "<sha256:" + sha256(raw.toByteArray()) + ">";
                        if (known.test(messageId)) {
                            continue;
                        }
                    }
                    result.add(new FetchedMail(messageId, received == null ? clock.instant() : received.toInstant(),
                            raw.toByteArray()));
                }
            } finally {
                folder.close(false);
            }
        } catch (MessagingException | IOException e) {
            throw new ImapException(e.getMessage(), e);
        }
        return result;
    }

    private static Store connect(Config config) throws MessagingException {
        var protocol = config.useTls() ? "imaps" : "imap";
        var props = new Properties();
        props.setProperty("mail.store.protocol", protocol);
        props.setProperty("mail." + protocol + ".peek", "true");
        props.setProperty("mail." + protocol + ".connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.setProperty("mail." + protocol + ".timeout", String.valueOf(TIMEOUT_MS));
        props.setProperty("mail.mime.allowutf8", "true");
        if (config.useTls()) {
            props.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        }
        var store = Session.getInstance(props).getStore(protocol);
        store.connect(config.host(), config.port(), config.username(), config.password());
        return store;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
