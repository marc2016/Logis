package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/** One IMAP mailbox per user; the password is stored encrypted. */
@Entity
@Table(name = "mailboxes", schema = "vacationrental")
class Mailbox {

    @Id
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_enc", nullable = false)
    private String passwordEnc;

    @Column(nullable = false)
    private String folder;

    @Column(name = "use_tls", nullable = false)
    private boolean useTls;

    @Column(name = "cutoff_date", nullable = false)
    private LocalDate cutoffDate;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_poll_at")
    private Instant lastPollAt;

    @Column(name = "last_error")
    private String lastError;

    @Version
    @Column(nullable = false)
    private long version;

    protected Mailbox() {
    }

    Mailbox(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    Long getOwnerUserId() {
        return ownerUserId;
    }

    String getHost() {
        return host;
    }

    int getPort() {
        return port;
    }

    String getUsername() {
        return username;
    }

    String getPasswordEnc() {
        return passwordEnc;
    }

    String getFolder() {
        return folder;
    }

    boolean isUseTls() {
        return useTls;
    }

    LocalDate getCutoffDate() {
        return cutoffDate;
    }

    boolean isEnabled() {
        return enabled;
    }

    Instant getLastPollAt() {
        return lastPollAt;
    }

    String getLastError() {
        return lastError;
    }

    void apply(String host, int port, String username, String passwordEnc, String folder, boolean useTls,
            LocalDate cutoffDate, boolean enabled) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.passwordEnc = passwordEnc;
        this.folder = folder;
        this.useTls = useTls;
        this.cutoffDate = cutoffDate;
        this.enabled = enabled;
    }

    void polled(Instant at, String error) {
        if (error == null) {
            this.lastPollAt = at;
        }
        this.lastError = error == null ? null : error.length() > 255 ? error.substring(0, 255) : error;
    }
}
