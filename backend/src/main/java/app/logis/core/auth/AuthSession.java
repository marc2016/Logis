package app.logis.core.auth;

import app.logis.core.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Instant;

/**
 * Access to the sign-in state in the HTTP session.
 */
final class AuthSession {

    static final String USER_ID = "logis.auth.userId";
    static final String STAGE = "logis.auth.stage";
    static final String VERSION = "logis.auth.version";
    static final String LOGIN_AT = "logis.auth.loginAt";
    static final String LAST_SEEN = "logis.auth.lastSeen";
    static final String PENDING_TOTP_SECRET = "logis.auth.pendingTotpSecret";
    /** The sign-in asked for „Angemeldet bleiben“; the device is remembered once it completes. */
    static final String REMEMBER_REQUESTED = "logis.auth.rememberRequested";
    /** Id of the remembered device this session belongs to, if any. */
    static final String DEVICE_ID = "logis.auth.deviceId";
    /** The session was started by a remembered device, not by a sign-in. */
    static final String RESTORED = "logis.auth.restored";
    /** Set by „Erneut anmelden“: the remembered device must not start a session until the next sign-in. */
    static final String SKIP_RESTORE = "logis.auth.skipRestore";
    /** When the remembered device's last use was last written (at most hourly). */
    static final String DEVICE_TOUCHED_AT = "logis.auth.deviceTouchedAt";

    private AuthSession() {
    }

    /** Discards an existing session and starts a new one (protection against session fixation). */
    static HttpSession start(HttpServletRequest request, Long userId, int version, AuthStage stage, Instant now) {
        var old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        var session = request.getSession(true);
        session.setAttribute(USER_ID, userId);
        session.setAttribute(VERSION, version);
        session.setAttribute(STAGE, stage.name());
        session.setAttribute(LOGIN_AT, now);
        session.setAttribute(LAST_SEEN, now);
        return session;
    }

    /** Moves to the next stage and issues a new session ID. */
    static void advance(HttpServletRequest request, AuthStage stage, int version) {
        var session = request.getSession(false);
        session.setAttribute(STAGE, stage.name());
        session.setAttribute(VERSION, version);
        session.removeAttribute(PENDING_TOTP_SECRET);
        request.changeSessionId();
    }

    static void updateVersion(HttpServletRequest request, int version) {
        var session = request.getSession(false);
        if (session != null) {
            session.setAttribute(VERSION, version);
        }
    }

    static AuthStage stage(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || session.getAttribute(STAGE) == null) {
            return AuthStage.ANONYMOUS;
        }
        return AuthStage.valueOf((String) session.getAttribute(STAGE));
    }

    /** Returns the user ID if the session is in exactly the expected stage. */
    static Long requireStage(HttpServletRequest request, AuthStage expected) {
        if (stage(request) != expected) {
            throw ApiException.unauthorized("Die Anmeldung ist abgelaufen. Bitte erneut anmelden.");
        }
        return (Long) request.getSession(false).getAttribute(USER_ID);
    }

    static void invalidate(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
