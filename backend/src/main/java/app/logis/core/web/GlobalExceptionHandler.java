package app.logis.core.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Einheitliche Fehlerantworten als ProblemDetail - ohne Stacktraces, Exception-Namen oder Versionen.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status())
                .body(ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unerwarteter Fehler", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ein unerwarteter Fehler ist aufgetreten."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("Die Eingaben sind ungueltig.");
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Standardfehler von Spring MVC (404, 405, 415 ...) ohne technische Details ausliefern.
        var problem = ProblemDetail.forStatus(statusCode);
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }
}
