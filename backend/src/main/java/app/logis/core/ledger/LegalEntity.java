package app.logis.core.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Eigentuemer von Konten (steuerliche Trennung). Vergibt selbst keine Rechte.
 */
@Entity
@Table(name = "legal_entities", schema = "core")
class LegalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LegalEntityKind kind;

    @Column(name = "tax_number", length = 32)
    private String taxNumber;

    @Column(name = "manager_user_id", nullable = false, updatable = false)
    private Long managerUserId;

    @Column(nullable = false, updatable = false)
    private boolean personal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LegalEntity() {
    }

    LegalEntity(String name, LegalEntityKind kind, String taxNumber, Long managerUserId, boolean personal,
            Instant createdAt) {
        this.name = name;
        this.kind = kind;
        this.taxNumber = taxNumber;
        this.managerUserId = managerUserId;
        this.personal = personal;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    LegalEntityKind getKind() {
        return kind;
    }

    void setKind(LegalEntityKind kind) {
        this.kind = kind;
    }

    String getTaxNumber() {
        return taxNumber;
    }

    void setTaxNumber(String taxNumber) {
        this.taxNumber = taxNumber;
    }

    Long getManagerUserId() {
        return managerUserId;
    }

    boolean isPersonal() {
        return personal;
    }
}
