-- Remembered devices ("Angemeldet bleiben"). Only an addition: existing data stays unchanged.

CREATE TABLE core.remembered_devices
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    -- Random identifier of the sign-in that remembered the device; stays the same across rotations.
    series              BYTEA       NOT NULL,
    -- SHA-256 of the current token; the token itself is only in the device's cookie.
    token_hash          BYTEA       NOT NULL,
    previous_token_hash BYTEA,
    rotated_at          TIMESTAMPTZ,
    -- core.users.session_version at issue time; a different current value revokes the device.
    session_version     INT         NOT NULL,
    browser             VARCHAR(32) NOT NULL,
    os                  VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX remembered_devices_series_uq ON core.remembered_devices (series);
CREATE INDEX remembered_devices_user_idx ON core.remembered_devices (user_id);
