package app.logis.modules.vacationrental;

/** Review of bookings coming from mails: a new booking or a proposed change waits for confirmation. */
enum ReviewState {
    NONE,
    NEW,
    CHANGE
}
