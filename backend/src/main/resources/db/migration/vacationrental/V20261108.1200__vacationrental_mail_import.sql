-- Mail import: one IMAP mailbox per user, fetched mails with their result, and unit mappings per agency.
CREATE TABLE vacationrental.mailboxes
(
    owner_user_id BIGINT PRIMARY KEY,
    host          VARCHAR(255) NOT NULL,
    port          INTEGER      NOT NULL CHECK (port BETWEEN 1 AND 65535),
    username      VARCHAR(255) NOT NULL,
    -- Encrypted with SecretCipher; never returned by the API.
    password_enc  TEXT         NOT NULL,
    folder        VARCHAR(255) NOT NULL DEFAULT 'INBOX',
    use_tls       BOOLEAN      NOT NULL DEFAULT TRUE,
    cutoff_date   DATE         NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_poll_at  TIMESTAMPTZ,
    last_error    VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE vacationrental.imported_mails
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL,
    message_id    VARCHAR(512) NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL,
    sender        VARCHAR(255),
    subject       VARCHAR(998),
    raw           BYTEA        NOT NULL,
    text_preview  TEXT,
    agency        VARCHAR(64),
    mail_type     VARCHAR(16) CHECK (mail_type IN ('CONFIRMATION', 'SHORT_NOTICE', 'CHANGE', 'CANCELLATION')),
    parsed        JSONB,
    parsed_by     VARCHAR(8)   NOT NULL DEFAULT 'NONE' CHECK (parsed_by IN ('RULE', 'AI', 'NONE')),
    result        VARCHAR(24)  NOT NULL,
    result_detail VARCHAR(255),
    booking_id    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (owner_user_id, message_id)
);

CREATE INDEX imported_mails_owner_idx ON vacationrental.imported_mails (owner_user_id, received_at DESC);
CREATE INDEX imported_mails_booking_idx ON vacationrental.imported_mails (booking_id);

CREATE TABLE vacationrental.unit_mappings
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL,
    agency        VARCHAR(64)  NOT NULL,
    property_name VARCHAR(255) NOT NULL,
    unit_id       BIGINT       NOT NULL
);

CREATE UNIQUE INDEX unit_mappings_uq ON vacationrental.unit_mappings (owner_user_id, agency, lower(property_name));

-- Bookings: agency, pets, review of imported bookings and proposed changes.
ALTER TABLE vacationrental.bookings
    ADD COLUMN agency       VARCHAR(64),
    ADD COLUMN pets         SMALLINT    NOT NULL DEFAULT 0 CHECK (pets >= 0),
    ADD COLUMN review_state VARCHAR(8)  NOT NULL DEFAULT 'NONE' CHECK (review_state IN ('NONE', 'NEW', 'CHANGE')),
    ADD COLUMN proposal     JSONB;

-- A booking number is unique per unit and agency (bookings without agency count as one agency).
DROP INDEX vacationrental.bookings_external_ref_uq;
CREATE UNIQUE INDEX bookings_external_ref_uq
    ON vacationrental.bookings (unit_id, lower(coalesce(agency, '')), lower(external_ref));
