package app.logis.core.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "recovery_codes", schema = "core")
class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecoveryCode() {
    }

    RecoveryCode(Long userId, String codeHash, Instant createdAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
    }

    String getCodeHash() {
        return codeHash;
    }

    void markUsed(Instant now) {
        this.usedAt = now;
    }
}
