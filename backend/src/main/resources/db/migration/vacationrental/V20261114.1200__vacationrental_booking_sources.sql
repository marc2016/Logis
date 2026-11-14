-- Sources of a booking (the text a mail was read from) and where each field's value was found in them.
-- Only new tables: existing bookings are untouched; their sources are derived once at startup from the
-- mail documents already attached to them.

CREATE TABLE vacationrental.booking_sources
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id  BIGINT       NOT NULL REFERENCES vacationrental.bookings (id) ON DELETE CASCADE,
    -- The document in core.documents the source belongs to; no foreign key across module schemas.
    document_id BIGINT       NOT NULL,
    -- 'MAIL' today; later kinds such as 'PDF' add their own location parts.
    kind        VARCHAR(16)  NOT NULL,
    label       VARCHAR(255) NOT NULL,
    sender      VARCHAR(320),
    received_at TIMESTAMPTZ,
    subject     TEXT,
    text        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX booking_sources_document_uq ON vacationrental.booking_sources (document_id);
CREATE INDEX booking_sources_booking_idx ON vacationrental.booking_sources (booking_id);

CREATE TABLE vacationrental.booking_field_sources
(
    booking_id   BIGINT      NOT NULL REFERENCES vacationrental.bookings (id) ON DELETE CASCADE,
    -- 'CURRENT' for the booking's values, 'PROPOSAL' for the values of a proposed change.
    scope        VARCHAR(16) NOT NULL,
    field        VARCHAR(32) NOT NULL,
    source_id    BIGINT      NOT NULL REFERENCES vacationrental.booking_sources (id) ON DELETE CASCADE,
    -- 'SUBJECT' or 'TEXT'; offsets are UTF-16 code units into that string of the source.
    part         VARCHAR(16) NOT NULL,
    start_pos    INT         NOT NULL,
    end_pos      INT         NOT NULL,
    -- The value as read from the source, normalized (ISO dates, plain decimals).
    source_value TEXT,
    PRIMARY KEY (booking_id, scope, field),
    CHECK (scope IN ('CURRENT', 'PROPOSAL')),
    CHECK (start_pos >= 0 AND end_pos >= start_pos)
);

CREATE INDEX booking_field_sources_source_idx ON vacationrental.booking_field_sources (source_id);
