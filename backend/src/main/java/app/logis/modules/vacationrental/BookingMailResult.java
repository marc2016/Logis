package app.logis.modules.vacationrental;

/** What a booking mail did; shown in the inbox (labels in the frontend module). */
enum BookingMailResult {
    BOOKING_CREATED, CHANGE_PROPOSED, ATTACHED, UNASSIGNED_UNIT, NOT_MATCHED, PERIOD_TAKEN, FAILED, DISCARDED
}
