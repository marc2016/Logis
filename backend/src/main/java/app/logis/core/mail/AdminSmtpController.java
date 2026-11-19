package app.logis.core.mail;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** „E-Mail-Versand“ in the admin area (design D3 of outgoing-mail). Never returns the password. */
@RestController
@RequestMapping("/api/admin/smtp")
class AdminSmtpController {

    private final SmtpSettingsService service;

    AdminSmtpController(SmtpSettingsService service) {
        this.service = service;
    }

    record SmtpView(boolean configured, String host, Integer port, SmtpSettings.Security security, String username,
            boolean hasPassword, String fromAddress, String fromName, Instant updatedAt) {

        static SmtpView of(SmtpSettings s) {
            return new SmtpView(true, s.getHost(), s.getPort(), s.getSecurity(), s.getUsername(),
                    s.getPasswordEnc() != null, s.getFromAddress(), s.getFromName(), s.getUpdatedAt());
        }
    }

    record TestRequest(String to) {
    }

    @GetMapping
    SmtpView get() {
        return service.get().map(SmtpView::of)
                .orElse(new SmtpView(false, null, null, null, null, false, null, null, null));
    }

    @PutMapping
    SmtpView save(@RequestBody SmtpSettingsService.Input input) {
        return SmtpView.of(service.save(input));
    }

    @DeleteMapping
    ResponseEntity<Void> delete() {
        service.delete();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    ResponseEntity<Void> test(@RequestBody TestRequest body) {
        service.sendTest(body.to());
        return ResponseEntity.noContent().build();
    }
}
