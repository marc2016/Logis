package app.logis.modules.vacationrental;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A change proposed by a mail for an existing booking. {@code null} fields stay unchanged; a cancellation carries
 * no fields.
 *
 * @param mailId the imported mail the proposal comes from
 */
record BookingProposal(Type type, LocalDate arrival, LocalDate departure, Integer adults, Integer children,
        Integer pets, BigDecimal expectedRent, Guest guest, Long mailId) {

    enum Type {
        CHANGE,
        CANCELLATION
    }

    static BookingProposal cancellation(Long mailId) {
        return new BookingProposal(Type.CANCELLATION, null, null, null, null, null, null, null, mailId);
    }
}
