package app.logis.core.ledger;

import app.logis.core.auth.LogisPrincipal;
import com.fasterxml.jackson.annotation.JsonFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
class TransactionController {

    private final TransactionService service;
    private final TransactionViews views;

    TransactionController(TransactionService service, TransactionViews views) {
        this.service = service;
        this.views = views;
    }

    record TransactionPage(List<TransactionViews.TransactionView> items, int page, int size, long totalItems,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal income,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal expense) {
    }

    record TransactionRequest(Long accountId, LocalDate bookingDate, BigDecimal amount, Long categoryId,
            String counterparty, String purpose, String note, Long propertyId, Long unitId, Integer version) {

        TransactionService.TransactionInput input() {
            return new TransactionService.TransactionInput(accountId, bookingDate, amount, categoryId, counterparty,
                    purpose, note, propertyId, unitId);
        }
    }

    record TransferRequest(Long fromAccountId, Long toAccountId, LocalDate bookingDate, BigDecimal amount,
            String note, Integer version) {

        TransactionService.TransferInput input() {
            return new TransactionService.TransferInput(fromAccountId, toAccountId, bookingDate, amount, note);
        }
    }

    @GetMapping("/api/transactions")
    TransactionPage list(@RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean uncategorized,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal LogisPrincipal me) {
        var filter = new TransactionFilter(accountId, propertyId, unitId, from, to, categoryId, uncategorized, q);
        var result = service.list(me.userId(), filter, page, size);
        return new TransactionPage(views.of(result.page().getContent(), me.userId()), result.page().getNumber(),
                result.page().getSize(), result.page().getTotalElements(), result.income(), result.expense());
    }

    @PostMapping("/api/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionViews.TransactionView create(@RequestBody TransactionRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return views.of(List.of(service.create(me.userId(), body.input())), me.userId()).getFirst();
    }

    @PutMapping("/api/transactions/{id}")
    TransactionViews.TransactionView update(@PathVariable Long id, @RequestBody TransactionRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return views.of(List.of(service.update(me.userId(), id, body.input(), body.version())), me.userId())
                .getFirst();
    }

    @DeleteMapping("/api/transactions/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal LogisPrincipal me) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    List<TransactionViews.TransactionView> createTransfer(@RequestBody TransferRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        return views.of(service.createTransfer(me.userId(), body.input()), me.userId());
    }

    @PutMapping("/api/transfers/{transferId}")
    List<TransactionViews.TransactionView> updateTransfer(@PathVariable UUID transferId,
            @RequestBody TransferRequest body, @AuthenticationPrincipal LogisPrincipal me) {
        return views.of(service.updateTransfer(me.userId(), transferId, body.input(), body.version()), me.userId());
    }

    @DeleteMapping("/api/transfers/{transferId}")
    ResponseEntity<Void> deleteTransfer(@PathVariable UUID transferId, @AuthenticationPrincipal LogisPrincipal me) {
        service.deleteTransfer(me.userId(), transferId);
        return ResponseEntity.noContent().build();
    }
}
