package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class CalendarLinkController {

    private static final MediaType CALENDAR = new MediaType("text", "calendar", java.nio.charset.StandardCharsets.UTF_8);

    private final CalendarLinkService service;

    CalendarLinkController(CalendarLinkService service) {
        this.service = service;
    }

    record CreateRequest(Long unitId, String name, boolean withGuests) {
    }

    @GetMapping("/api/vacationrental/calendar-links")
    List<CalendarLinkService.LinkView> list(@RequestParam Long unitId, @AuthenticationPrincipal LogisPrincipal me) {
        return service.list(me.userId(), unitId);
    }

    @PostMapping("/api/vacationrental/calendar-links")
    @ResponseStatus(HttpStatus.CREATED)
    CalendarLinkService.CreatedLink create(@RequestBody CreateRequest request, @AuthenticationPrincipal LogisPrincipal me) {
        return service.create(me.userId(), request.unitId(), request.name(), request.withGuests());
    }

    @DeleteMapping("/api/vacationrental/calendar-links/{id}")
    ResponseEntity<Void> revoke(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.revoke(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Public feed for calendar apps: no session, the secret token is the only credential. */
    @GetMapping("/api/ical/{token}.ics")
    ResponseEntity<String> feed(@PathVariable String token) {
        return service.feed(token)
                .map(body -> ResponseEntity.ok().contentType(CALENDAR).header("Cache-Control", "no-store").body(body))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
