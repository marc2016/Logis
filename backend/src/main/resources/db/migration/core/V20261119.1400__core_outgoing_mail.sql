-- Outgoing mail: the admin's SMTP settings, an e-mail address per user and pending address changes.
-- Only additions: existing users keep no address, and nothing is sent until an admin configures a server.

ALTER TABLE core.users ADD COLUMN email VARCHAR(254);

-- At most one row (id = 1); no row means sending is not set up.
CREATE TABLE core.smtp_settings
(
    id           SMALLINT     PRIMARY KEY CHECK (id = 1),
    host         VARCHAR(255) NOT NULL,
    port         INTEGER      NOT NULL CHECK (port BETWEEN 1 AND 65535),
    security     VARCHAR(16)  NOT NULL CHECK (security IN ('NONE', 'STARTTLS', 'SSL_TLS')),
    username     VARCHAR(255),
    -- Encrypted with SecretCipher; never returned by the API.
    password_enc TEXT,
    from_address VARCHAR(254) NOT NULL,
    from_name    VARCHAR(128),
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- A user's own address change, waiting for the code sent to the new address.
CREATE TABLE core.email_changes
(
    user_id    BIGINT       PRIMARY KEY REFERENCES core.users (id) ON DELETE CASCADE,
    new_email  VARCHAR(254) NOT NULL,
    code_hash  BYTEA        NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    attempts   INTEGER      NOT NULL DEFAULT 0
);
