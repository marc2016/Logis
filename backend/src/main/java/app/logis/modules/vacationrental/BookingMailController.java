package app.logis.modules.vacationrental;

import app.logis.core.auth.LogisPrincipal;
import app.logis.core.mail.Mails;
import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.sharing.ShareRole;
import app.logis.core.web.ApiException;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Booking-specific inbox action: map the agency's property name to a unit and process the waiting mails. */
@RestController
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingMailController {

    private final Mails mails;
    private final UnitMappingRepository mappings;
    private final RentalAccess access;
    private final AgencyService agencies;

    BookingMailController(Mails mails, UnitMappingRepository mappings, RentalAccess access, AgencyService agencies) {
        this.mails = mails;
        this.mappings = mappings;
        this.access = access;
        this.agencies = agencies;
    }

    record AssignRequest(Long unitId) {
    }

    @PostMapping("/api/vacationrental/mail/{mailId}/assign")
    ResponseEntity<Void> assign(@PathVariable Long mailId, @RequestBody AssignRequest body,
            @AuthenticationPrincipal LogisPrincipal me) {
        var mail = mails.get(me.userId(), mailId);
        if (!BookingMailHandler.KEY.equals(mail.handler()) || mail.subjectName() == null) {
            throw ApiException.badRequest("Die Mail nennt kein Objekt.");
        }
        access.require(body.unitId(), me.userId(), ShareRole.EDITOR, BookingService.UNIT_NOT_FOUND);
        var agencyId = agencies.resolve(mail.source()).map(Agency::getId)
                .orElseThrow(() -> ApiException.badRequest("Die Mail nennt keinen Vermittler."));
        var mapping = mappings.find(me.userId(), agencyId, mail.subjectName())
                .orElseGet(() -> new UnitMapping(me.userId(), agencyId, mail.subjectName(), body.unitId()));
        mapping.setUnitId(body.unitId());
        mappings.save(mapping);
        mails.reprocessWaiting(me.userId(), BookingMailHandler.KEY, BookingMailResult.UNASSIGNED_UNIT.name(),
                mail.source(), mail.subjectName());
        return ResponseEntity.noContent().build();
    }

    @EventListener
    void onDiscarded(ImportedBookingDiscarded event) {
        mails.markByLink(Booking.DOCUMENT_TYPE, event.bookingId(), BookingMailResult.DISCARDED.name());
    }
}
