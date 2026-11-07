package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/vacationrental")
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BookingService service;

    BookingController(BookingService service) {
        this.service = service;
    }

    record BookingRequest(Long unitId, LocalDate arrival, LocalDate departure, String externalRef, Channel channel,
            Integer adults, Integer children, BigDecimal expectedRent, Guest guest, Long version) {

        BookingService.BookingInput input() {
            return new BookingService.BookingInput(unitId, arrival, departure, externalRef, channel, adults,
                    children, expectedRent, guest);
        }
    }

    record UnitRef(Long id, String name, Long propertyId, String propertyName, boolean propertyMultiUnit) {
    }

    /** {@code guest} carries contact data only for editors; {@code guestDeleted} marks deleted guest data. */
    record BookingView(Long id, UnitRef unit, LocalDate arrival, LocalDate departure, long nights,
            String externalRef, Channel channel, int adults, int children, BigDecimal expectedRent,
            BookingStatus status, Long statementId, Guest guest, boolean guestDeleted, boolean canEdit,
            long version) {

        static BookingView of(BookingService.BookingDetails d) {
            var b = d.booking();
            var u = d.unit();
            return new BookingView(b.getId(),
                    new UnitRef(u.id(), u.name(), u.propertyId(), u.propertyName(), u.propertyMultiUnit()),
                    b.getArrival(), b.getDeparture(), b.nights(), b.getExternalRef(), b.getChannel(),
                    b.getAdults(), b.getChildren(), b.getExpectedRent(), d.status(), d.statementId(), d.guest(),
                    b.isGuestDeleted(), d.canEdit(), b.getVersion());
        }
    }

    record BookingPage(List<BookingView> items, int page, int size, long totalItems) {
    }

    record RentalUnitView(Long id, String name, Long propertyId, String propertyName, boolean propertyMultiUnit,
            boolean archived, ShareRole myRole) {
    }

    @GetMapping("/units")
    List<RentalUnitView> units(@AuthenticationPrincipal LogisPrincipal me) {
        return service.units(me.userId()).stream()
                .map(e -> new RentalUnitView(e.getKey().id(), e.getKey().name(), e.getKey().propertyId(),
                        e.getKey().propertyName(), e.getKey().propertyMultiUnit(),
                        e.getKey().archived() || e.getKey().propertyArchived(), e.getValue()))
                .toList();
    }

    /** {@code sort} is {@code field,direction}, e.g. {@code arrival,desc} (default). */
    @GetMapping("/bookings")
    BookingPage list(@RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "arrival,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal LogisPrincipal me) {
        var parts = sort.split(",");
        BookingService.SortKey key;
        try {
            // camelCase or snake_case: expectedRent, expected_rent
            key = BookingService.SortKey.valueOf(parts[0].trim().replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Ungültige Sortierung.");
        }
        var ascending = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc");
        var result = service.list(me.userId(), new BookingService.Filter(propertyId, unitId, status, from, to, q),
                key, ascending, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return new BookingPage(result.items().stream().map(BookingView::of).toList(), result.page(), result.size(),
                result.totalItems());
    }

    @GetMapping("/bookings/{id}")
    BookingView get(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return BookingView.of(service.get(me.userId(), id));
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    BookingView create(@RequestBody BookingRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        return BookingView.of(service.create(me.userId(), body.input()));
    }

    @PutMapping("/bookings/{id}")
    BookingView update(@PathVariable Long id, @RequestBody BookingRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return BookingView.of(service.update(me.userId(), id, body.input(), body.version()));
    }

    @PostMapping("/bookings/{id}/cancel")
    BookingView cancel(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return BookingView.of(service.setCancelled(me.userId(), id, true));
    }

    @PostMapping("/bookings/{id}/reinstate")
    BookingView reinstate(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return BookingView.of(service.setCancelled(me.userId(), id, false));
    }

    @DeleteMapping("/bookings/{id}/guest")
    BookingView deleteGuest(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return BookingView.of(service.deleteGuest(me.userId(), id));
    }

    @DeleteMapping("/bookings/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
