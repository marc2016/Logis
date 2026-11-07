package app.logis.modules.vacationrental;

/** Derived, never stored: cancelled wins, otherwise settled once a statement line exists. */
enum BookingStatus {
    PLANNED,
    SETTLED,
    CANCELLED
}
