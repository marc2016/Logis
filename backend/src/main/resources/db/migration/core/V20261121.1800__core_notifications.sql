-- Notifications: a user's switches per event type and channel, the admin's Pushover application token and the
-- users' own Pushover keys (design D3 of notifications-pushover).
-- Only additions: without a row a switch has its default, and nothing is sent through Pushover until an admin
-- stores a token and a user stores a key.

CREATE TABLE core.notification_settings
(
    user_id    BIGINT       NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    -- Key of the event type, e.g. vacationrental.bookingReview. Rows of unknown types are kept and ignored.
    event_type VARCHAR(100) NOT NULL,
    channel    VARCHAR(32)  NOT NULL,
    enabled    BOOLEAN      NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, event_type, channel)
);

-- At most one row (id = 1); no row means Pushover is not set up.
CREATE TABLE core.pushover_settings
(
    id            SMALLINT    PRIMARY KEY CHECK (id = 1),
    -- Encrypted with SecretCipher; never returned by the API.
    app_token_enc TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE core.pushover_user_keys
(
    user_id       BIGINT      PRIMARY KEY REFERENCES core.users (id) ON DELETE CASCADE,
    -- Encrypted with SecretCipher; never returned by the API.
    user_key_enc  TEXT        NOT NULL,
    -- Set when Pushover rejected the key; nothing is sent to it until the user stores a key again.
    invalid_since TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL
);
