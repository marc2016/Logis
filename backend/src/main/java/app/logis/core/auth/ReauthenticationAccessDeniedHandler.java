package app.logis.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.net.URI;

/**
 * Tells an admin whose session was started by a remembered device that the admin area needs a fresh sign-in
 * (design.md of remember-me, D9). Every other denial is handled as before.
 */
class ReauthenticationAccessDeniedHandler implements AccessDeniedHandler {

    static final URI TYPE = URI.create("urn:logis:reauthentication-required");
    static final String MESSAGE = "Für den Admin-Bereich bitte erneut anmelden";

    private final ObjectMapper json;
    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    ReauthenticationAccessDeniedHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException, ServletException {
        if (ex instanceof CsrfException || !request.getRequestURI().startsWith("/api/admin/") || !reauthRequired()) {
            fallback.handle(request, response, ex);
            return;
        }
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, MESSAGE);
        problem.setType(TYPE);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), problem);
    }

    private static boolean reauthRequired() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> SessionAuthenticationFilter.REAUTH_REQUIRED.equals(a.getAuthority()));
    }
}
