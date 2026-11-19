package app.logis.core.users;

import app.logis.core.mail.EmailAddress;
import app.logis.core.mail.MailSender;
import app.logis.core.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

/** A user's own address change, confirmed with a code sent to the new address (design D6 of outgoing-mail). */
@Service
class EmailChangeService {

    static final String NOT_SET_UP = "Der E-Mail-Versand ist nicht eingerichtet. Bitte wende dich an einen Admin.";
    static final String INVALID_CODE = "Der Code ist ungültig oder abgelaufen.";
    static final String SEND_FAILED = "Die Bestätigungsmail konnte nicht gesendet werden";
    static final String CODE_SUBJECT = "Bestätigungscode für Logis";
    static final String CHANGED_SUBJECT = "Deine E-Mail-Adresse in Logis wurde geändert.";
    static final Duration VALIDITY = Duration.ofMinutes(30);
    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(EmailChangeService.class);

    private final UserRepository users;
    private final MailSender mail;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    EmailChangeService(UserRepository users, MailSender mail, JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.mail = mail;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    record EmailView(String email, String pendingEmail, boolean canChange) {
    }

    @Transactional(readOnly = true)
    EmailView view(Long userId) {
        var user = users.findById(userId).orElseThrow();
        var pending = jdbc.query("SELECT new_email FROM core.email_changes WHERE user_id = ? AND expires_at > ? "
                        + "AND attempts < ?", (rs, i) -> rs.getString(1), userId, Timestamp.from(clock.instant()),
                MAX_ATTEMPTS).stream().findFirst();
        return new EmailView(user.getEmail(), pending.orElse(null), mail.configured());
    }

    /** Sends a code to the new address; replaces an earlier request. Nothing is kept if the mail fails. */
    @Transactional
    void request(Long userId, String email) {
        if (!mail.configured()) {
            throw new ApiException(HttpStatus.CONFLICT, NOT_SET_UP);
        }
        var address = EmailAddress.require(email, EmailAddress.INVALID);
        var code = "%06d".formatted(random.nextInt(1_000_000));
        jdbc.update("DELETE FROM core.email_changes WHERE user_id = ?", userId);
        try {
            mail.send(address, CODE_SUBJECT, """
                    Dein Bestätigungscode für die neue E-Mail-Adresse in Logis:

                    %s

                    Der Code gilt 30 Minuten. Wenn du keine Änderung angefordert hast, kannst du diese Mail ignorieren.
                    """.formatted(code));
        } catch (MailSender.MailSendException ex) {
            throw ApiException.badRequest(SEND_FAILED);
        }
        jdbc.update("INSERT INTO core.email_changes (user_id, new_email, code_hash, expires_at) VALUES (?, ?, ?, ?)",
                userId, address, hash(userId, code), Timestamp.from(clock.instant().plus(VALIDITY)));
    }

    /**
     * Sets the new address if the code is right. Not rolled back on a wrong code: the attempt must count (hence
     * {@code noRollbackFor}).
     */
    @Transactional(noRollbackFor = ApiException.class)
    String confirm(Long userId, String code) {
        var pending = jdbc.query("SELECT new_email, code_hash, expires_at, attempts FROM core.email_changes "
                        + "WHERE user_id = ? FOR UPDATE",
                (rs, i) -> new Pending(rs.getString(1), rs.getBytes(2), rs.getTimestamp(3).toInstant(), rs.getInt(4)),
                userId).stream().findFirst();
        if (pending.isEmpty() || !clock.instant().isBefore(pending.get().expiresAt())
                || pending.get().attempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest(INVALID_CODE);
        }
        var input = code == null ? "" : code.strip();
        if (!MessageDigest.isEqual(hash(userId, input), pending.get().codeHash())) {
            jdbc.update("UPDATE core.email_changes SET attempts = attempts + 1 WHERE user_id = ?", userId);
            throw ApiException.badRequest(INVALID_CODE);
        }
        var user = users.findById(userId).orElseThrow();
        var old = user.getEmail();
        var address = pending.get().newEmail();
        user.setEmail(address);
        jdbc.update("DELETE FROM core.email_changes WHERE user_id = ?", userId);
        notifyOld(old, address);
        return address;
    }

    /** Best effort: the change stays even if the notice cannot be sent. */
    private void notifyOld(String old, String address) {
        if (old == null || old.equalsIgnoreCase(address)) {
            return;
        }
        try {
            mail.send(old, CHANGED_SUBJECT, """
                    Deine E-Mail-Adresse in Logis wurde geändert. Neue Adresse: %s

                    Wenn du das nicht warst, melde dich bitte sofort bei einem Admin.
                    """.formatted(partial(address)));
        } catch (MailSender.MailSendException ex) {
            log.warn("Notice about a changed e-mail address could not be sent");
        }
    }

    /** „a…@example.org“: the first character of the local part and the domain. */
    static String partial(String address) {
        var at = address.indexOf('@');
        return address.substring(0, Math.min(1, at)) + "…" + address.substring(at);
    }

    private record Pending(String newEmail, byte[] codeHash, java.time.Instant expiresAt, int attempts) {
    }

    private static byte[] hash(Long userId, String code) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
