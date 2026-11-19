package app.logis.core.auth;

import app.logis.core.users.Role;
import app.logis.core.users.User;
import app.logis.core.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Checks the session against the current user state on every request:
 * inactivity, absolute duration, disabling and session version. Only fully signed-in
 * sessions get roles in the SecurityContext - with the currently stored role.
 * Without a session, a remembered device starts one (design.md of remember-me, D8).
 */
class SessionAuthenticationFilter extends OncePerRequestFilter {

    /** Authority of an admin whose session was started by a remembered device (design D9 of remember-me). */
    static final String REAUTH_REQUIRED = "REAUTH_REQUIRED";

    private final UserRepository users;
    private final AuthProperties properties;
    private final Clock clock;
    private final RememberedDeviceService devices;

    SessionAuthenticationFilter(UserRepository users, AuthProperties properties, Clock clock,
            RememberedDeviceService devices) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
        this.devices = devices;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        boolean signedIn = false;
        if (session != null && session.getAttribute(AuthSession.USER_ID) instanceof Long userId) {
            var now = clock.instant();
            var loginAt = (Instant) session.getAttribute(AuthSession.LOGIN_AT);
            var lastSeen = (Instant) session.getAttribute(AuthSession.LAST_SEEN);
            var version = (Integer) session.getAttribute(AuthSession.VERSION);
            var user = users.findById(userId).orElse(null);

            boolean expired = now.isAfter(loginAt.plus(properties.session().absoluteTimeout()))
                    || now.isAfter(lastSeen.plus(properties.session().idleTimeout()));
            boolean revoked = user == null || !user.isEnabled() || user.getSessionVersion() != version;

            if (expired || revoked) {
                session.invalidate();
            } else {
                signedIn = true;
                session.setAttribute(AuthSession.LAST_SEEN, now);
                if (AuthStage.AUTHENTICATED.name().equals(session.getAttribute(AuthSession.STAGE))) {
                    devices.touchIfDue(request, session);
                    authenticate(user, session);
                }
            }
        }
        // Without a device cookie no database access: health checks must work while the database is down.
        if (!signedIn && RememberedDeviceService.hasCookie(request) && mayRestore(request)) {
            devices.restore(request, response).ifPresent(restored -> {
                var user = restored.user();
                var restoredSession = AuthSession.start(request, user.getId(), user.getSessionVersion(),
                        AuthStage.AUTHENTICATED, clock.instant());
                restoredSession.setAttribute(AuthSession.DEVICE_ID, restored.deviceId());
                restoredSession.setAttribute(AuthSession.DEVICE_TOUCHED_AT, clock.instant());
                restoredSession.setAttribute(AuthSession.RESTORED, true);
                authenticate(user, restoredSession);
            });
        }
        chain.doFilter(request, response);
    }

    /**
     * Sign-in steps and sign-out never restore, and neither does a session in which „Erneut anmelden“ was chosen.
     */
    private static boolean mayRestore(HttpServletRequest request) {
        if ("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/auth/")) {
            return false;
        }
        var session = request.getSession(false);
        return session == null || session.getAttribute(AuthSession.SKIP_RESTORE) == null;
    }

    private static void authenticate(User user, HttpSession session) {
        var restored = Boolean.TRUE.equals(session.getAttribute(AuthSession.RESTORED));
        List<GrantedAuthority> authorities;
        if (user.getRole() != Role.ADMIN) {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        } else if (restored) {
            // No admin area without a fresh sign-in; domain data stays accessible.
            authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(REAUTH_REQUIRED));
        } else {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LogisPrincipal(user.getId(), user.getUsername()), null, authorities));
        SecurityContextHolder.setContext(context);
    }
}
