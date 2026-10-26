package app.logis.core.users;

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
@Table(name = "users", schema = "core")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "totp_secret_enc", length = 512)
    private String totpSecretEnc;

    @Column(name = "totp_last_step")
    private Long totpLastStep;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String username, String displayName, Role role, String passwordHash, Instant createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /** Beendet alle bestehenden Sitzungen dieses Benutzers bei deren naechster Anfrage. */
    public void invalidateSessions() {
        this.sessionVersion++;
    }

    public boolean isTotpEnabled() {
        return totpSecretEnc != null;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public String getTotpSecretEnc() {
        return totpSecretEnc;
    }

    public void setTotpSecretEnc(String totpSecretEnc) {
        this.totpSecretEnc = totpSecretEnc;
    }

    public Long getTotpLastStep() {
        return totpLastStep;
    }

    public void setTotpLastStep(Long totpLastStep) {
        this.totpLastStep = totpLastStep;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
