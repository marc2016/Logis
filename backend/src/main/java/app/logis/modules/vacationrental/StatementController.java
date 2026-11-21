package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import com.fasterxml.jackson.annotation.JsonFormat;
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

@RestController
@RequestMapping("/api/vacationrental/statements")
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class StatementController {

    private final StatementService service;

    StatementController(StatementService service) {
        this.service = service;
    }

    record StatementRequest(Long unitId, String provider, LocalDate periodFrom, LocalDate periodTo,
            LocalDate statementDate, String invoiceNumber, BigDecimal statedPayout, Long version) {

        StatementService.StatementInput input() {
            return new StatementService.StatementInput(unitId, provider, periodFrom, periodTo, statementDate,
                    invoiceNumber, statedPayout);
        }
    }

    record AssignOpenLineRequest(Long bookingId) {
    }

    record OpenLineView(Long id, String guestName, LocalDate arrival, LocalDate departure,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gross,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal commission, String note) {

        static OpenLineView of(StatementOpenLine o) {
            return new OpenLineView(o.getId(), o.getGuestName(), o.getArrival(), o.getDeparture(), o.getGross(),
                    o.getCommission(), o.getNote());
        }
    }

    record LineRequest(BigDecimal gross, BigDecimal commission, BigDecimal cleaning) {
    }

    record LineView(BookingController.BookingView booking,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gross,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal commission,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cleaning,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal net) {
    }

    record StatementView(Long id, BookingController.UnitRef unit, String provider, LocalDate periodFrom,
            LocalDate periodTo, LocalDate statementDate, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal payout,
            int lineCount, boolean canEdit,
            long version, List<LineView> lines, String invoiceNumber,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal statedPayout,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal payoutDifference, int openLineCount,
            List<OpenLineView> openLines, boolean toReview) {

        static StatementView of(StatementService.StatementDetails d) {
            var s = d.statement();
            var u = d.unit();
            return new StatementView(s.getId(),
                    new BookingController.UnitRef(u.id(), u.name(), u.propertyId(), u.propertyName(),
                            u.propertyMultiUnit()),
                    s.getProvider(), s.getPeriodFrom(), s.getPeriodTo(), s.getStatementDate(), d.payout(),
                    d.lineCount(), d.canEdit(), s.getVersion(),
                    d.lines().stream()
                            .map(l -> new LineView(BookingController.BookingView.of(l.booking()),
                                    l.line().getGross(), l.line().getCommission(), l.line().getCleaning(),
                                    l.line().net()))
                            .toList(),
                    s.getInvoiceNumber(), s.getStatedPayout(), d.payoutDifference(), d.openLineCount(),
                    d.openLines().stream().map(OpenLineView::of).toList(), d.toReview());
        }
    }

    @GetMapping
    List<StatementView> list(@RequestParam(required = false) Long unitId,
            @AuthenticationPrincipal LogisPrincipal me) {
        return service.list(me.userId(), unitId).stream().map(StatementView::of).toList();
    }

    @GetMapping("/providers")
    List<String> providers(@RequestParam Long unitId, @AuthenticationPrincipal LogisPrincipal me) {
        return service.providers(me.userId(), unitId);
    }

    @GetMapping("/{id}")
    StatementView get(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.get(me.userId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StatementView create(@RequestBody StatementRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.create(me.userId(), body.input()));
    }

    @PutMapping("/{id}")
    StatementView update(@PathVariable Long id, @RequestBody StatementRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.update(me.userId(), id, body.input(), body.version()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/lines/{bookingId}")
    StatementView putLine(@PathVariable Long id, @PathVariable Long bookingId, @RequestBody LineRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.putLine(me.userId(), id, bookingId,
                new StatementService.LineInput(body.gross(), body.commission(), body.cleaning())));
    }

    @PostMapping("/{id}/open-lines/{openLineId}/assign")
    StatementView assignOpenLine(@PathVariable Long id, @PathVariable Long openLineId,
            @RequestBody AssignOpenLineRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.assignOpenLine(me.userId(), id, openLineId, body.bookingId()));
    }

    @DeleteMapping("/{id}/open-lines/{openLineId}")
    StatementView discardOpenLine(@PathVariable Long id, @PathVariable Long openLineId,
            @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.discardOpenLine(me.userId(), id, openLineId));
    }

    @DeleteMapping("/{id}/lines/{bookingId}")
    StatementView removeLine(@PathVariable Long id, @PathVariable Long bookingId,
            @AuthenticationPrincipal LogisPrincipal me) {
        return StatementView.of(service.removeLine(me.userId(), id, bookingId));
    }
}
