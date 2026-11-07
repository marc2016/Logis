package app.logis.modules.vacationrental;

import app.logis.core.documents.DocumentOwnerType;
import app.logis.core.modules.ConditionalOnLogisModule;
import org.springframework.stereotype.Component;

/** Booking documents usually contain guest contact data: reading and writing both need EDITOR on the unit. */
@Component
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
class BookingDocumentOwner implements DocumentOwnerType {

    private final BookingService bookings;

    BookingDocumentOwner(BookingService bookings) {
        this.bookings = bookings;
    }

    @Override
    public String type() {
        return Booking.DOCUMENT_TYPE;
    }

    @Override
    public boolean canRead(Long ownerId, Long userId) {
        return bookings.canEditBooking(ownerId, userId);
    }

    @Override
    public boolean canWrite(Long ownerId, Long userId) {
        return bookings.canEditBooking(ownerId, userId);
    }
}
