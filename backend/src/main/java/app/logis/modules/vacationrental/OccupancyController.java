package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/vacationrental/occupancy")
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class OccupancyController {

    private final OccupancyService service;

    OccupancyController(OccupancyService service) {
        this.service = service;
    }

    /** {@code month} as {@code 2026-11}. */
    @GetMapping("/calendar")
    OccupancyService.Calendar calendar(@RequestParam Long propertyId, @RequestParam String month,
            @AuthenticationPrincipal LogisPrincipal me) {
        YearMonth parsed;
        try {
            parsed = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Ungültiger Monat.");
        }
        return service.calendar(me.userId(), propertyId, parsed);
    }

    @GetMapping("/overview")
    OccupancyService.Overview overview(@RequestParam Long unitId, @RequestParam int year,
            @AuthenticationPrincipal LogisPrincipal me) {
        if (year < 1900 || year > 2999) {
            throw ApiException.badRequest("Ungültiges Jahr.");
        }
        return service.overview(me.userId(), unitId, year);
    }
}
