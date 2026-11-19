-- Sign-in warnings: a history of complete sign-ins (no IP addresses) and the location of remembered devices.
-- Only additions: existing users have no history, existing remembered devices no location.

CREATE TABLE core.sign_ins
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    at          TIMESTAMPTZ  NOT NULL,
    -- SHA-256 of the device cookie LOGIS_DEVICE; the cookie itself is only in the browser.
    device_hash BYTEA        NOT NULL,
    browser     VARCHAR(32)  NOT NULL,
    os          VARCHAR(32)  NOT NULL,
    -- ISO 3166 code and city from the local GeoIP database; null if unknown.
    country     CHAR(2),
    city        VARCHAR(128)
);

CREATE INDEX sign_ins_user_at_idx ON core.sign_ins (user_id, at DESC);

ALTER TABLE core.remembered_devices ADD COLUMN country CHAR(2);
ALTER TABLE core.remembered_devices ADD COLUMN city VARCHAR(128);
