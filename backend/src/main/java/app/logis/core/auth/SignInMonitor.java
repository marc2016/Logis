package app.logis.core.auth;

import app.logis.shared.GermanTime;
import app.logis.core.geo.GeoLocator;
import app.logis.core.notifications.Notification;
import app.logis.core.notifications.NotificationType;
import app.logis.core.notifications.Notifications;
import app.logis.core.users.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

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
 * sign-in history and warns the user about a new device or country (spec sign-in-alerts, design D1–D3). The warning
 * is the notification „Neue Anmeldung“: always by mail, and by other channels the user switched on (design D5 of
 * notifications-pushover).
 */
@Component
class SignInMonitor {

    static final String COOKIE = "LOGIS_DEVICE";
    static final String SUBJECT = "Neue Anmeldung bei Logis";
    /** Event type of the warning; its mail cannot be switched off. */
    static final NotificationType TYPE = NotificationType.of("core.signIn", "Neue Anmeldung", "Sicherheit",
            java.util.Set.of("mail"));
    static final Duration HISTORY = Duration.ofDays(180);
    private static final Duration COOKIE_LIFETIME = Duration.ofDays(365);
    private static final ZoneId ZONE = GermanTime.ZONE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN);

    private final JdbcTemplate jdbc;
    private final GeoLocator geo;
    private final Notifications notifications;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    SignInMonitor(JdbcTemplate jdbc, GeoLocator geo, Notifications notifications, Clock clock) {
        this.jdbc = jdbc;
        this.geo = geo;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Bean
    static NotificationType signInNotificationType() {
        return TYPE;
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

        var warn = !first && (newDevice || newLocation);
        if (warn) {
            // In the background once the sign-in is stored, so the sign-in never waits for or fails with it. Users
            // without an address get no mail, but still the channels they switched on.
            notifications.send(TYPE.key(), java.util.List.of(user.getId()),
                    new Notification(SUBJECT, shortText(family, location), text(now, family, location), null));
        }
        return new Outcome(first, newDevice, newLocation, warn);
    }

    /** For channels outside the system: device and place, never the client address. */
    static String shortText(DeviceFamily family, GeoLocator.Location location) {
        var browser = DeviceFamily.OTHER.equals(family.browser()) ? "Unbekannter Browser" : family.browser();
        var os = DeviceFamily.OTHER.equals(family.os()) ? "unbekanntes System" : family.os();
        return "%s auf %s, %s".formatted(browser, os, location == null ? "Ort unbekannt" : location.label());
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
