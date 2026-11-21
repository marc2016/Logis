package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A row of the provider's statement document that is on no booking yet (spec rental-statements, „Open lines“). It
 * does not count in the payout until it is assigned to a booking.
 */
@Entity
@Table(name = "statement_open_lines", schema = "vacationrental")
class StatementOpenLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_id", nullable = false, updatable = false)
    private Long statementId;

    @Column(name = "guest_name", nullable = false, length = 128)
    private String guestName;

    @Column(nullable = false)
    private LocalDate arrival;

    @Column(nullable = false)
    private LocalDate departure;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commission;

    @Column(length = 128)
    private String note;

    protected StatementOpenLine() {
    }

    StatementOpenLine(Long statementId, String guestName, LocalDate arrival, LocalDate departure, BigDecimal gross,
            BigDecimal commission, String note) {
        this.statementId = statementId;
        this.guestName = guestName.length() > 128 ? guestName.substring(0, 128) : guestName;
        this.arrival = arrival;
        this.departure = departure;
        this.gross = gross;
        this.commission = commission;
        this.note = note;
    }

    Long getId() {
        return id;
    }

    Long getStatementId() {
        return statementId;
    }

    String getGuestName() {
        return guestName;
    }

    LocalDate getArrival() {
        return arrival;
    }

    LocalDate getDeparture() {
        return departure;
    }

    BigDecimal getGross() {
        return gross;
    }

    BigDecimal getCommission() {
        return commission;
    }

    String getNote() {
        return note;
    }
}
