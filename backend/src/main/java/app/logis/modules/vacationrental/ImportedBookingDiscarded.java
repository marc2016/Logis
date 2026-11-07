package app.logis.modules.vacationrental;

/** A booking created from a mail was discarded before confirmation; the mail import marks its mail. */
record ImportedBookingDiscarded(Long bookingId) {
}
