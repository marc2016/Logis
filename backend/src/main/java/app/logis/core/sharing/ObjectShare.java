package app.logis.core.sharing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "object_shares", schema = "core")
public class ObjectShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_type", nullable = false, length = 64)
    private String objectType;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShareRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ObjectShare() {
    }

    ObjectShare(String objectType, Long objectId, Long userId, ShareRole role, Instant createdAt) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getObjectId() {
        return objectId;
    }

    public Long getUserId() {
        return userId;
    }

    public ShareRole getRole() {
        return role;
    }

    void setRole(ShareRole role) {
        this.role = role;
    }
}
