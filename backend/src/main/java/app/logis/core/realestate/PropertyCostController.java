package app.logis.core.realestate;

import app.logis.core.auth.LogisPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
class PropertyCostController {

    private final PropertyCostService service;

    PropertyCostController(PropertyCostService service) {
        this.service = service;
    }

    record CostRequest(Long unitId, LocalDate date, BigDecimal amount, Long categoryId, String counterparty,
            String note, Long version) {

        PropertyCostService.CostInput input() {
            return new PropertyCostService.CostInput(unitId, date, amount, categoryId, counterparty, note);
        }
    }

    record CostView(Long id, Long propertyId, Long unitId, LocalDate date, BigDecimal amount, Long categoryId,
            String counterparty, String note, long version) {

        static CostView of(PropertyCost c) {
            return new CostView(c.getId(), c.getPropertyId(), c.getUnitId(), c.getDate(), c.getAmount(),
                    c.getCategoryId(), c.getCounterparty(), c.getNote(), c.getVersion());
        }
    }

    @PostMapping("/properties/{propertyId}/costs")
    @ResponseStatus(HttpStatus.CREATED)
    CostView create(@PathVariable Long propertyId, @RequestBody CostRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return CostView.of(service.create(me.userId(), propertyId, body.input()));
    }

    @GetMapping("/costs/{id}")
    CostView get(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return CostView.of(service.get(me.userId(), id));
    }

    @PutMapping("/costs/{id}")
    CostView update(@PathVariable Long id, @RequestBody CostRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return CostView.of(service.update(me.userId(), id, body.input(), body.version()));
    }

    @DeleteMapping("/costs/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
