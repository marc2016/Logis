package app.logis.core.realestate;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
class PropertyFinanceController {

    private final PropertyFinanceService service;

    PropertyFinanceController(PropertyFinanceService service) {
        this.service = service;
    }

    /** {@code sort} is {@code field,direction}, e.g. {@code date,desc} (default). */
    @GetMapping("/api/properties/{id}/finances")
    PropertyFinanceService.Finances get(@PathVariable Long id, @RequestParam int year,
            @RequestParam(required = false) Long unitId, @RequestParam(required = false) FinanceItem.Kind kind,
            @RequestParam(defaultValue = "date,desc") String sort, @AuthenticationPrincipal LogisPrincipal me) {
        if (year < 1900 || year > 2999) {
            throw ApiException.badRequest("Ungültiges Jahr.");
        }
        var parts = sort.split(",");
        PropertyFinanceService.SortKey key;
        try {
            key = PropertyFinanceService.SortKey.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Ungültige Sortierung.");
        }
        var ascending = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc");
        return service.get(me.userId(), id, year, new PropertyFinanceService.Filter(unitId, kind, key, ascending));
    }
}
