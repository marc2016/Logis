package app.logis.core.auth;

import app.logis.core.users.Role;
import app.logis.core.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Prueft bei jeder Anfrage die Sitzung gegen den aktuellen Benutzerstand:
 * Inaktivitaet, absolute Dauer, Deaktivierung und Sitzungsversion. Nur vollstaendig angemeldete
 * Sitzungen erhalten Rollen im SecurityContext - mit der aktuell gespeicherten Rolle.
 */
class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final AuthProperties properties;
    private final Clock clock;

    SessionAuthenticationFilter(UserRepository users, AuthProperties properties, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
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
                session.setAttribute(AuthSession.LAST_SEEN, now);
                if (AuthStage.AUTHENTICATED.name().equals(session.getAttribute(AuthSession.STAGE))) {
                    var authorities = user.getRole() == Role.ADMIN
                            ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            new LogisPrincipal(user.getId(), user.getUsername()), null, authorities));
                    SecurityContextHolder.setContext(context);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
