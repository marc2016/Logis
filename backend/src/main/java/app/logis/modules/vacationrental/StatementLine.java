package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A booking settled on a statement; keyed by the booking, so it is on at most one statement. */
@Entity
@Table(name = "statement_lines", schema = "vacationrental")
class StatementLine {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commission;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cleaning;

    protected StatementLine() {
    }

    StatementLine(Long bookingId, Long statementId) {
        this.bookingId = bookingId;
        this.statementId = statementId;
    }

    Long getBookingId() {
        return bookingId;
    }

    Long getStatementId() {
        return statementId;
    }

    BigDecimal getGross() {
        return gross;
    }

    BigDecimal getCommission() {
        return commission;
    }

    BigDecimal getCleaning() {
        return cleaning;
    }

    /** Gross minus commission minus cleaning. */
    BigDecimal net() {
        return gross.subtract(commission).subtract(cleaning);
    }

    void setAmounts(BigDecimal gross, BigDecimal commission, BigDecimal cleaning) {
        this.gross = gross;
        this.commission = commission;
        this.cleaning = cleaning;
    }
}
