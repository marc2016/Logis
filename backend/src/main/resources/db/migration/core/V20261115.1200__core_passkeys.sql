-- Passkeys (WebAuthn credentials) as an alternative sign-in. Only additions: existing users stay unchanged.

-- Random user handle that passkeys carry instead of the user id; set on the first registration.
ALTER TABLE core.users ADD COLUMN webauthn_user_handle BYTEA;
CREATE UNIQUE INDEX users_webauthn_user_handle_uq ON core.users (webauthn_user_handle);

CREATE TABLE core.passkeys
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    credential_id   BYTEA       NOT NULL,
    public_key_cose BYTEA       NOT NULL,
    sign_count      BIGINT      NOT NULL DEFAULT 0,
    transports      VARCHAR(128),
    name            VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX passkeys_credential_id_uq ON core.passkeys (credential_id);
CREATE INDEX passkeys_user_idx ON core.passkeys (user_id);
