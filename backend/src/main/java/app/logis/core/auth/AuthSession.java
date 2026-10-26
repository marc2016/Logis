package app.logis.core.auth;

import app.logis.core.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Instant;

/**
 * Zugriff auf den Login-Zustand in der HTTP-Session.
 */
final class AuthSession {

    static final String USER_ID = "logis.auth.userId";
    static final String STAGE = "logis.auth.stage";
    static final String VERSION = "logis.auth.version";
    static final String LOGIN_AT = "logis.auth.loginAt";
    static final String LAST_SEEN = "logis.auth.lastSeen";
    static final String PENDING_TOTP_SECRET = "logis.auth.pendingTotpSecret";

    private AuthSession() {
    }

    /** Verwirft eine bestehende Sitzung und beginnt eine neue (Schutz vor Session Fixation). */
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

    /** Wechselt in die naechste Stufe und vergibt dabei eine neue Session-ID. */
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

    /** Liefert die Benutzer-ID, sofern die Sitzung genau in der erwarteten Stufe ist. */
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
