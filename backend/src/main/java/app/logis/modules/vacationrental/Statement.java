package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/** A rental service provider's statement for one unit; its payout is the sum of its lines' net amounts. */
@Entity
@Table(name = "statements", schema = "vacationrental")
class Statement {

    /** Source type of the finance items it contributes. */
    static final String SOURCE_TYPE = "vacationrental.statement";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private Long unitId;

    @Column(nullable = false, length = 128)
    private String provider;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Statement() {
    }

    Statement(Long unitId, Instant createdAt) {
        this.unitId = unitId;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getUnitId() {
        return unitId;
    }

    String getProvider() {
        return provider;
    }

    LocalDate getPeriodFrom() {
        return periodFrom;
    }

    LocalDate getPeriodTo() {
        return periodTo;
    }

    LocalDate getStatementDate() {
        return statementDate;
    }

    long getVersion() {
        return version;
    }

    void apply(String provider, LocalDate periodFrom, LocalDate periodTo, LocalDate statementDate) {
        this.provider = provider;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.statementDate = statementDate;
    }
}
