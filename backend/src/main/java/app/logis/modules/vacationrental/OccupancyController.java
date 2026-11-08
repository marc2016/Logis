package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/vacationrental/occupancy")
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class OccupancyController {

    private final OccupancyService service;

    OccupancyController(OccupancyService service) {
        this.service = service;
    }

    /** The year with December of the previous year and January of the next year. */
    @GetMapping("/calendar")
    OccupancyService.Calendar calendar(@RequestParam Long propertyId, @RequestParam int year,
            @AuthenticationPrincipal LogisPrincipal me) {
        requireYear(year);
        return service.calendar(me.userId(), propertyId, year);
    }

    @GetMapping("/overview")
    OccupancyService.Overview overview(@RequestParam Long unitId, @RequestParam int year,
            @AuthenticationPrincipal LogisPrincipal me) {
        requireYear(year);
        return service.overview(me.userId(), unitId, year);
    }

    private static void requireYear(int year) {
        if (year < 1900 || year > 2999) {
            throw ApiException.badRequest("Ungültiges Jahr.");
        }
    }
}
