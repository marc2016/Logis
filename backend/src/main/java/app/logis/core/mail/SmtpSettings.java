package app.logis.core.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** The system's one outgoing mail server, configured by an admin (design D1 of outgoing-mail). */
@Entity
@Table(name = "smtp_settings", schema = "core")
class SmtpSettings {

    static final short ID = 1;

    enum Security {
        NONE, STARTTLS, SSL_TLS
    }

    @Id
    private Short id = ID;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Security security;

    private String username;

    @Column(name = "password_enc")
    private String passwordEnc;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** A wrapper, so that a new row (with its fixed id) counts as new for Spring Data. */
    @Version
    @Column(nullable = false)
    private Long version;

    protected SmtpSettings() {
    }

    static SmtpSettings create() {
        return new SmtpSettings();
    }

    void apply(String host, int port, Security security, String username, String passwordEnc, String fromAddress,
            String fromName, Instant now) {
        this.host = host;
        this.port = port;
        this.security = security;
        this.username = username;
        this.passwordEnc = passwordEnc;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.updatedAt = now;
    }

    String getHost() {
        return host;
    }

    int getPort() {
        return port;
    }

    Security getSecurity() {
        return security;
    }

    String getUsername() {
        return username;
    }

    String getPasswordEnc() {
        return passwordEnc;
    }

    String getFromAddress() {
        return fromAddress;
    }

    String getFromName() {
        return fromName;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
