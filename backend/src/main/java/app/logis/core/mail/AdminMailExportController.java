package app.logis.core.mail;

import app.logis.shared.GermanTime;
import app.logis.core.auth.LogisPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * „Mail-Export“ in the admin area (design.md of admin-mail-export, D2). {@code /api/admin/**} needs the role
 * {@code ADMIN} and a fresh sign-in.
 */
@RestController
@RequestMapping("/api/admin/mail-export")
class AdminMailExportController {

    private final MailExportService exports;
    private final Clock clock;

    AdminMailExportController(MailExportService exports, Clock clock) {
        this.exports = exports;
        this.clock = clock;
    }

    record LogEntry(Long id, Instant at, String adminName, Long ownerUserId, String ownerName, int mailCount,
            String filters) {
    }

    @GetMapping("/mailboxes")
    List<MailExportService.ExportMailbox> mailboxes() {
        return exports.mailboxes();
    }

    @GetMapping("/agencies")
    List<String> agencies(@RequestParam(required = false) List<Long> owners) {
        return exports.agencies(owners == null ? List.of() : owners);
    }

    /** The ZIP; consent and size are checked before the response starts. */
    @PostMapping
    void export(@RequestBody MailExportService.Filter filter, @AuthenticationPrincipal LogisPrincipal me,
            HttpServletResponse response) throws IOException {
        exports.check(filter);
        var date = GermanTime.today(clock);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"logis-mails-" + date + ".zip\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        exports.export(me.userId(), filter, response.getOutputStream());
    }

    @GetMapping("/log")
    List<LogEntry> log() {
        return exports.log().stream().map(e -> new LogEntry(e.id(), e.at(), e.adminName(), e.ownerUserId(),
                e.ownerName(), e.mailCount(), e.filters())).toList();
    }
}
