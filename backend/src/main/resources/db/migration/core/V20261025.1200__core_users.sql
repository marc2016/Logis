-- Benutzer, Rollen, Zwei-Faktor-Anmeldung, Sperre nach Fehlversuchen.
CREATE TABLE core.users
(
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username                 VARCHAR(64)  NOT NULL,
    display_name             VARCHAR(128) NOT NULL,
    role                     VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    password_hash            VARCHAR(255) NOT NULL,
    password_change_required BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_secret_enc          VARCHAR(512),
    totp_last_step           BIGINT,
    failed_attempts          INTEGER      NOT NULL DEFAULT 0,
    locked_until             TIMESTAMPTZ,
    -- Wird bei Deaktivierung, Passwort- oder 2FA-Reset erhoeht und beendet damit alle Sitzungen.
    session_version          INTEGER      NOT NULL DEFAULT 0,
    last_login_at            TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_username_lower_uq ON core.users (lower(username));

CREATE TABLE core.recovery_codes
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(255) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX recovery_codes_user_idx ON core.recovery_codes (user_id);
