package app.logis.core.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts", schema = "core")
class Account {

    /** Objekttyp fuer Freigaben. */
    static final String SHARE_TYPE = "account";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_entity_id", nullable = false, updatable = false)
    private Long legalEntityId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountKind kind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "EUR";

    @Column(length = 34)
    private String iban;

    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    Account(Long legalEntityId, String name, AccountKind kind, String iban, BigDecimal openingBalance,
            Instant createdAt) {
        this.legalEntityId = legalEntityId;
        this.name = name;
        this.kind = kind;
        this.iban = iban;
        this.openingBalance = openingBalance;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getLegalEntityId() {
        return legalEntityId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    AccountKind getKind() {
        return kind;
    }

    void setKind(AccountKind kind) {
        this.kind = kind;
    }

    String getCurrency() {
        return currency;
    }

    String getIban() {
        return iban;
    }

    void setIban(String iban) {
        this.iban = iban;
    }

    BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    boolean isArchived() {
        return archived;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }
}
