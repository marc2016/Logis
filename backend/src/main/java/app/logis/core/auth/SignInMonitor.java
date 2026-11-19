package app.logis.core.auth;

import app.logis.core.geo.GeoLocator;
import app.logis.core.mail.MailSender;
import app.logis.core.users.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Watches complete sign-ins: recognises the device by a long-lived cookie, locates the client address, keeps the
 * sign-in history and warns the user by mail about a new device or country (spec sign-in-alerts, design D1–D3).
 */
@Component
class SignInMonitor {

    static final String COOKIE = "LOGIS_DEVICE";
    static final String SUBJECT = "Neue Anmeldung bei Logis";
    static final Duration HISTORY = Duration.ofDays(180);
    private static final Duration COOKIE_LIFETIME = Duration.ofDays(365);
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN);
    private static final Logger log = LoggerFactory.getLogger(SignInMonitor.class);

    private final JdbcTemplate jdbc;
    private final GeoLocator geo;
    private final MailSender mail;
    private final TaskExecutor executor;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    SignInMonitor(JdbcTemplate jdbc, GeoLocator geo, MailSender mail,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor, Clock clock) {
        this.jdbc = jdbc;
        this.geo = geo;
        this.mail = mail;
        this.executor = executor;
        this.clock = clock;
    }

    /** What the check found; for tests and logs. */
    record Outcome(boolean first, boolean newDevice, boolean newLocation, boolean warned) {
    }

    /** Called once per complete sign-in (not for sessions a remembered device starts). */
    Outcome signedIn(HttpServletRequest request, HttpServletResponse response, User user) {
        var now = clock.instant();
        var device = deviceId(request);
        writeCookie(request, response, device);
        var hash = hash(device);
        var family = DeviceFamily.of(request.getHeader(HttpHeaders.USER_AGENT));
        var location = geo.locate(request.getRemoteAddr()).orElse(null);

        var since = Timestamp.from(now.minus(HISTORY));
        var known = jdbc.queryForList("SELECT DISTINCT encode(device_hash, 'hex') FROM core.sign_ins "
                + "WHERE user_id = ? AND at >= ?", String.class, user.getId(), since);
        var countries = jdbc.queryForList("SELECT DISTINCT country FROM core.sign_ins WHERE user_id = ? AND at >= ? "
                + "AND country IS NOT NULL", String.class, user.getId(), since);
        var first = jdbc.queryForObject("SELECT NOT EXISTS (SELECT 1 FROM core.sign_ins WHERE user_id = ?)",
                Boolean.class, user.getId());
        var newDevice = !known.contains(java.util.HexFormat.of().formatHex(hash));
        var newLocation = location != null && !countries.contains(location.countryCode());

        jdbc.update("INSERT INTO core.sign_ins (user_id, at, device_hash, browser, os, country, city) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", user.getId(), Timestamp.from(now), hash, family.browser(),
                family.os(), location == null ? null : location.countryCode(),
                location == null ? null : truncate(location.city(), 128));

        var warn = !first && (newDevice || newLocation) && user.getEmail() != null;
        if (warn) {
            var to = user.getEmail();
            var text = text(now, family, location);
            afterCommit(() -> send(user.getId(), to, text));
        }
        return new Outcome(first, newDevice, newLocation, warn);
    }

    private void send(Long userId, String to, String text) {
        try {
            if (mail.configured()) {
                mail.send(to, SUBJECT, text);
            }
        } catch (RuntimeException ex) {
            // Informational only: the sign-in has succeeded and is recorded.
            log.warn("Sign-in warning could not be sent for user {}", userId);
        }
    }

    /** The mail is sent in the background once the sign-in is stored, so the sign-in never waits for it. */
    private void afterCommit(Runnable task) {
        Runnable background = () -> executor.execute(task);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    background.run();
                }
            });
        } else {
            background.run();
        }
    }

    static String text(Instant at, DeviceFamily family, GeoLocator.Location location) {
        var browser = DeviceFamily.OTHER.equals(family.browser()) ? "Unbekannter Browser" : family.browser();
        var os = DeviceFamily.OTHER.equals(family.os()) ? "unbekanntes System" : family.os();
        return """
                Dein Konto bei Logis wurde von einem neuen Gerät oder Ort aus angemeldet.

                Zeitpunkt: %s
                Gerät: %s auf %s
                Ort: %s

                Warst du das? Dann musst du nichts tun.

                Wenn nicht: Ändere sofort dein Passwort und wähle unter „Mein Konto“ „Alle anderen Geräte abmelden“.
                """.formatted(TIME.format(at.atZone(ZONE)), browser, os,
                location == null ? "unbekannt" : location.label());
    }

    /** Daily: removes sign-ins older than 180 days. */
    @Scheduled(cron = "0 23 3 * * *")
    void deleteOld() {
        jdbc.update("DELETE FROM core.sign_ins WHERE at < ?", Timestamp.from(clock.instant().minus(HISTORY)));
    }

    /** The presented device identifier, or a new one. */
    private String deviceId(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null
                        && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) {
                    return cookie.getValue();
                }
            }
        }
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Kept and renewed on every sign-in; signing out does not remove it (design D1). */
    private static void writeCookie(HttpServletRequest request, HttpServletResponse response, String device) {
        var cookie = ResponseCookie.from(COOKIE, device).httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                .path("/api").maxAge(COOKIE_LIFETIME).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static byte[] hash(String device) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(device.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
