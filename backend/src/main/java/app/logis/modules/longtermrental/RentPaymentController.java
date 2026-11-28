package app.logis.modules.longtermrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.modules.ConditionalOnLogisModule;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.http.HttpStatus;
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
import java.time.YearMonth;
import java.util.List;

/** The rent account of a tenancy and its payments (design D4 of rent-payments). */
@RestController
@RequestMapping("/api/longtermrental/tenancies/{tenancyId}")
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class RentPaymentController {

    record PaymentRequest(LocalDate date, BigDecimal amount, Long accountId, String note, Long version) {

        RentPaymentService.PaymentInput input() {
            return new RentPaymentService.PaymentInput(date, amount, accountId, note);
        }
    }

    record MonthView(@JsonFormat(shape = JsonFormat.Shape.STRING) YearMonth month,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal due,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paid,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal open, boolean overdue) {
    }

    /** {@code accountId} and {@code accountName} only for editors. */
    record PaymentView(Long id, LocalDate date, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            Long accountId, String accountName, String note, long version) {
    }

    record RentAccountView(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal due,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paid,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal arrears,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal credit, boolean overdue, boolean canEdit,
            List<MonthView> months, List<PaymentView> payments) {

        static RentAccountView of(RentPaymentService.RentAccount a) {
            var d = a.details();
            var account = d.account();
            var editor = d.canEdit();
            // Newest first, as the owner looks for the latest months.
            var months = account.months().reversed().stream()
                    .map(m -> new MonthView(m.month(), m.due(), m.paid(), m.open(), m.overdue())).toList();
            var payments = d.payments().reversed().stream()
                    .map(p -> new PaymentView(p.id(), p.date(), p.amount(),
                            editor ? a.accountIds().get(p.transactionId()) : null,
                            editor ? a.accountNames().get(p.transactionId()) : null, editor ? p.note() : null,
                            p.version()))
                    .toList();
            return new RentAccountView(account.due(), account.paid(), account.arrears(), account.credit(),
                    account.overdue(), editor, months, payments);
        }
    }

    private final RentPaymentService service;

    RentPaymentController(RentPaymentService service) {
        this.service = service;
    }

    @GetMapping("/rent-account")
    RentAccountView account(@PathVariable Long tenancyId, @AuthenticationPrincipal LogisPrincipal me) {
        return RentAccountView.of(service.account(me.userId(), tenancyId));
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    RentAccountView record(@PathVariable Long tenancyId, @RequestBody PaymentRequest request,
            @AuthenticationPrincipal LogisPrincipal me) {
        return RentAccountView.of(service.record(me.userId(), tenancyId, request.input()));
    }

    @PutMapping("/payments/{paymentId}")
    RentAccountView update(@PathVariable Long tenancyId, @PathVariable Long paymentId,
            @RequestBody PaymentRequest request, @AuthenticationPrincipal LogisPrincipal me) {
        return RentAccountView.of(service.update(me.userId(), tenancyId, paymentId, request.input(), request.version()));
    }

    @DeleteMapping("/payments/{paymentId}")
    RentAccountView delete(@PathVariable Long tenancyId, @PathVariable Long paymentId,
            @AuthenticationPrincipal LogisPrincipal me) {
        return RentAccountView.of(service.delete(me.userId(), tenancyId, paymentId));
    }
}
