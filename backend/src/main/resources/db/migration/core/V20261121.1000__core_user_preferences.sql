-- Settings per user and key, e.g. the view of the booking page or the columns of a list (design.md of
-- booking-compact-view, D1). Only an addition: users without a row see the defaults.

CREATE TABLE core.user_preferences
(
    user_id    BIGINT       NOT NULL REFERENCES core.users (id) ON DELETE CASCADE,
    key        VARCHAR(100) NOT NULL,
    value      JSONB        NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, key)
);
