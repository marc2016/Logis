package app.logis.core.ledger;

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
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "core")
class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(length = 255)
    private String counterparty;

    @Column(length = 500)
    private String purpose;

    @Column
    private String note;

    /** Beide Seiten einer Umbuchung teilen sich diese ID. */
    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    Transaction(Long accountId, UUID transferId, Long userId, Instant now) {
        this.accountId = accountId;
        this.transferId = transferId;
        this.createdBy = userId;
        this.createdAt = now;
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    void touch(Long userId, Instant now) {
        this.updatedBy = userId;
        this.updatedAt = now;
    }

    boolean isTransfer() {
        return transferId != null;
    }

    Long getId() {
        return id;
    }

    Long getAccountId() {
        return accountId;
    }

    void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    LocalDate getBookingDate() {
        return bookingDate;
    }

    void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    Long getCategoryId() {
        return categoryId;
    }

    void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    String getCounterparty() {
        return counterparty;
    }

    void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    String getPurpose() {
        return purpose;
    }

    void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    String getNote() {
        return note;
    }

    void setNote(String note) {
        this.note = note;
    }

    UUID getTransferId() {
        return transferId;
    }

    int getVersion() {
        return version;
    }
}
