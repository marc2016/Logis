package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder: proves that the module is active and reachable.
 */
@RestController
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class VacationRentalStatusController {

    @GetMapping("/api/vacationrental/status")
    Map<String, String> status() {
        return Map.of("module", VacationRentalConfiguration.MODULE_ID, "status", "ok");
    }
}
