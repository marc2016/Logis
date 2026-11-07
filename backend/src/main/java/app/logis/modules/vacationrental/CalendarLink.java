package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** A secret link to the read-only calendar feed of one unit. Only the hash of its token is stored. */
@Entity
@Table(name = "calendar_links", schema = "vacationrental")
class CalendarLink {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "with_guests", nullable = false)
    private boolean withGuests;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarLink() {
    }

    CalendarLink(Long unitId, Long createdBy, String name, boolean withGuests, String token, Instant createdAt) {
        this.unitId = unitId;
        this.createdBy = createdBy;
        this.name = name;
        this.withGuests = withGuests;
        this.tokenHash = hash(token);
        this.createdAt = createdAt;
    }

    /** 32 random bytes, URL-safe (43 characters). */
    static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    Long getId() {
        return id;
    }

    Long getUnitId() {
        return unitId;
    }

    Long getCreatedBy() {
        return createdBy;
    }

    String getName() {
        return name;
    }

    boolean isWithGuests() {
        return withGuests;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
