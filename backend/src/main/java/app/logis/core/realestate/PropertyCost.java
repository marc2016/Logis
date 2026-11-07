package app.logis.core.realestate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A cost recorded directly on a property ({@code unitId == null}) or one of its units, without an account. */
@Entity
@Table(name = "property_costs", schema = "core")
class PropertyCost {

    /** Owner type for documents (receipts). */
    static final String DOCUMENT_TYPE = "realestate.cost";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private Long propertyId;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "cost_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(length = 128)
    private String counterparty;

    @Column
    private String note;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PropertyCost() {
    }

    PropertyCost(Long propertyId, Instant createdAt) {
        this.propertyId = propertyId;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getPropertyId() {
        return propertyId;
    }

    Long getUnitId() {
        return unitId;
    }

    LocalDate getDate() {
        return date;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Long getCategoryId() {
        return categoryId;
    }

    String getCounterparty() {
        return counterparty;
    }

    String getNote() {
        return note;
    }

    long getVersion() {
        return version;
    }

    void apply(Long unitId, LocalDate date, BigDecimal amount, Long categoryId, String counterparty, String note) {
        this.unitId = unitId;
        this.date = date;
        this.amount = amount;
        this.categoryId = categoryId;
        this.counterparty = counterparty;
        this.note = note;
    }
}
