package app.logis.core.auth;

import app.logis.core.geo.GeoLocator;
import app.logis.core.users.User;
import app.logis.core.users.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Remembered devices („Angemeldet bleiben“, design.md of remember-me): issues the device cookie when a sign-in
 * completes, starts sessions from it, rotates its token and revokes it.
 */
@Service
class RememberedDeviceService {

    static final String COOKIE = "LOGIS_REMEMBER";

    private static final Logger log = LoggerFactory.getLogger(RememberedDeviceService.class);
    private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final RememberedDeviceStore store;
    private final UserRepository users;
    private final LoginAttemptService attempts;
    private final AuthProperties.RememberMe settings;
    private final GeoLocator geo;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RememberedDeviceService(RememberedDeviceStore store, UserRepository users, LoginAttemptService attempts,
            AuthProperties properties, Clock clock, GeoLocator geo) {
        this.geo = geo;
        this.store = store;
        this.users = users;
        this.attempts = attempts;
        this.settings = properties.rememberMe();
        this.clock = clock;
    }

    record Restored(User user, Long deviceId) {
    }

    /** {@code city} and {@code countryName}: where the device was last used, if known (design D6 of sign-in-alerts). */
    record DeviceView(Long id, String browser, String os, Instant createdAt, Instant lastUsedAt, boolean current,
            String city, String countryName) {
    }

    private record Presented(byte[] series, byte[] token) {
    }

    /**
     * Called by every sign-in step that completes the sign-in: forgets the device cookie the request carries (it
     * may even belong to another user) and remembers the device if the sign-in asked for it (design D7).
     */
    @Transactional
    void onAuthenticated(HttpServletRequest request, HttpServletResponse response, User user) {
        forgetPresented(request, response);
        var session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(AuthSession.REMEMBER_REQUESTED))) {
            return;
        }
        session.removeAttribute(AuthSession.REMEMBER_REQUESTED);
        var now = clock.instant();
        var series = randomBytes();
        var token = randomBytes();
        var expiresAt = now.plus(settings.absolute());
        var id = store.insert(user.getId(), series, hash(token), user.getSessionVersion(),
                DeviceFamily.of(request.getHeader(HttpHeaders.USER_AGENT)), now, expiresAt, locate(request));
        session.setAttribute(AuthSession.DEVICE_ID, id);
        session.setAttribute(AuthSession.DEVICE_TOUCHED_AT, now);
        writeCookie(request, response, series, token, expiresAt, now);
    }

    /**
     * Starts nothing itself: checks the device cookie and, if it is valid, rotates its token and returns the user
     * and device the caller starts the session for (design D3–D6).
     */
    @Transactional
    Optional<Restored> restore(HttpServletRequest request, HttpServletResponse response) {
        var presented = presented(request);
        if (presented == null) {
            return Optional.empty();
        }
        var device = store.bySeriesForUpdate(presented.series()).orElse(null);
        if (device == null) {
            clearCookie(request, response);
            return Optional.empty();
        }
        var now = clock.instant();
        var user = users.findById(device.userId()).orElse(null);
        var expired = now.isAfter(device.expiresAt()) || now.isAfter(device.lastUsedAt().plus(settings.sliding()));
        if (expired || user == null || !user.isEnabled() || user.getSessionVersion() != device.sessionVersion()) {
            store.delete(device.id());
            clearCookie(request, response);
            return Optional.empty();
        }
        var family = DeviceFamily.of(request.getHeader(HttpHeaders.USER_AGENT));
        if (!family.browser().equals(device.browser()) || !family.os().equals(device.os())) {
            revoke(device, user, request, response, "REMEMBER_DEVICE_CHANGED");
            return Optional.empty();
        }
        // Another country than at the last use: treated like a stolen cookie. Unknown on either side: not compared.
        var location = locate(request);
        if (location != null && device.country() != null && !device.country().equals(location.countryCode())) {
            revoke(device, user, request, response, "REMEMBER_COUNTRY_CHANGED");
            return Optional.empty();
        }
        var presentedHash = hash(presented.token());
        if (MessageDigest.isEqual(presentedHash, device.tokenHash())) {
            var token = randomBytes();
            store.rotate(device.id(), hash(token), device.tokenHash(), now, location);
            writeCookie(request, response, device.series(), token, device.expiresAt(), now);
            return Optional.of(new Restored(user, device.id()));
        }
        var withinGrace = device.previousTokenHash() != null && device.rotatedAt() != null
                && !now.isAfter(device.rotatedAt().plus(settings.reuseGrace()));
        if (withinGrace && MessageDigest.isEqual(presentedHash, device.previousTokenHash())) {
            // A request sent together with the one that rotated: the browser already has the new cookie.
            store.touch(device.id(), now, location);
            return Optional.of(new Restored(user, device.id()));
        }
        revoke(device, user, request, response, "REMEMBER_REUSE");
        return Optional.empty();
    }

    /** Sign-out: forgets the device of the presented cookie and clears the cookie (design D10). */
    @Transactional
    void forget(HttpServletRequest request, HttpServletResponse response) {
        forgetPresented(request, response);
    }

    /** Keeps the device's last use current while its session is active, writing at most once per hour (design D5). */
    void touchIfDue(HttpServletRequest request, HttpSession session) {
        if (!(session.getAttribute(AuthSession.DEVICE_ID) instanceof Long deviceId)) {
            return;
        }
        var now = clock.instant();
        var touched = (Instant) session.getAttribute(AuthSession.DEVICE_TOUCHED_AT);
        if (touched == null || !now.isBefore(touched.plus(TOUCH_INTERVAL))) {
            store.touch(deviceId, now, locate(request));
            session.setAttribute(AuthSession.DEVICE_TOUCHED_AT, now);
        }
    }

    /**
     * After the user's session version was increased by the user themselves: the current device stays remembered,
     * all other devices are forgotten (design D6).
     */
    @Transactional
    void keepOnlyCurrent(HttpServletRequest request, User user) {
        var currentId = currentDeviceId(request);
        if (currentId != null) {
            store.updateSessionVersion(currentId, user.getSessionVersion());
        }
        store.deleteOthers(user.getId(), currentId);
    }

    List<DeviceView> list(User user, HttpServletRequest request) {
        var currentId = currentDeviceId(request);
        return store.current(user.getId(), user.getSessionVersion()).stream()
                .map(d -> new DeviceView(d.id(), d.browser(), d.os(), d.createdAt(), d.lastUsedAt(),
                        d.id().equals(currentId), d.city(), countryName(d.country())))
                .toList();
    }

    /** Returns whether the user had such a device. */
    @Transactional
    boolean signOut(Long userId, Long deviceId) {
        return store.deleteOwn(userId, deviceId);
    }

    /** Daily: removes devices that can no longer start a session. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void deleteStale() {
        var now = clock.instant();
        var count = store.deleteStale(now, now.minus(settings.sliding()));
        if (count > 0) {
            log.info("Removed {} expired remembered devices", count);
        }
    }

    private GeoLocator.Location locate(HttpServletRequest request) {
        return geo.locate(request.getRemoteAddr()).orElse(null);
    }

    /** German name of an ISO country code, e.g. „Deutschland“. */
    private static String countryName(String code) {
        if (code == null) {
            return null;
        }
        var name = new java.util.Locale.Builder().setRegion(code).build().getDisplayCountry(java.util.Locale.GERMAN);
        return name.isBlank() ? code : name;
    }

    private static Long currentDeviceId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute(AuthSession.DEVICE_ID) instanceof Long id ? id : null;
    }

    private void revoke(RememberedDeviceStore.Device device, User user, HttpServletRequest request,
            HttpServletResponse response, String reason) {
        store.delete(device.id());
        clearCookie(request, response);
        // Logged, but not counted towards the lockout: only the holder of a cookie can cause it (design D3).
        attempts.logFailure(user.getUsername(), request.getRemoteAddr(), reason);
    }

    private void forgetPresented(HttpServletRequest request, HttpServletResponse response) {
        var presented = presented(request);
        if (presented != null) {
            store.deleteBySeries(presented.series());
        }
        if (hasCookie(request)) {
            clearCookie(request, response);
        }
    }

    /** Whether the request carries a device cookie; checked before {@link #restore} opens a transaction. */
    static boolean hasCookie(HttpServletRequest request) {
        return cookieValue(request) != null;
    }

    private static String cookieValue(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static Presented presented(HttpServletRequest request) {
        var value = cookieValue(request);
        if (value == null) {
            return null;
        }
        var parts = value.split(":", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            var series = DECODER.decode(parts[0]);
            var token = DECODER.decode(parts[1]);
            return series.length == 32 && token.length == 32 ? new Presented(series, token) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void writeCookie(HttpServletRequest request, HttpServletResponse response, byte[] series, byte[] token,
            Instant expiresAt, Instant now) {
        var cookie = baseCookie(request, ENCODER.encodeToString(series) + ":" + ENCODER.encodeToString(token))
                .maxAge(Duration.between(now, expiresAt))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static void clearCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(request, "").maxAge(0).build().toString());
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(HttpServletRequest request, String value) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api");
    }

    private byte[] randomBytes() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return bytes;
    }

    static byte[] hash(byte[] token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
