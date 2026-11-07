-- Overlap check of stays per unit (exclusion constraint with "=" on unit_id).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- unit_id refers to core.units without a foreign key: the core asks the module before deleting (UnitReferences).
CREATE TABLE vacationrental.bookings
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_id           BIGINT        NOT NULL,
    arrival           DATE          NOT NULL,
    departure         DATE          NOT NULL,
    external_ref      VARCHAR(64),
    channel           VARCHAR(16) CHECK (channel IN ('AIRBNB', 'BOOKING_COM', 'DIRECT', 'OTHER')),
    adults            SMALLINT      NOT NULL CHECK (adults >= 1),
    children          SMALLINT      NOT NULL DEFAULT 0 CHECK (children >= 0),
    expected_rent     NUMERIC(12, 2) CHECK (expected_rent >= 0),
    cancelled         BOOLEAN       NOT NULL DEFAULT FALSE,
    guest_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    guest_name        VARCHAR(128),
    guest_street      VARCHAR(128),
    guest_postal_code VARCHAR(16),
    guest_city        VARCHAR(128),
    guest_country     VARCHAR(64),
    guest_phone       VARCHAR(64),
    guest_email       VARCHAR(254),
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (departure > arrival),
    -- Half-open range: arrival on another stay's departure day is allowed. Cancelled stays free the period.
    CONSTRAINT bookings_no_overlap EXCLUDE USING gist (unit_id WITH =, daterange(arrival, departure) WITH &&)
        WHERE (NOT cancelled)
);

CREATE UNIQUE INDEX bookings_external_ref_uq ON vacationrental.bookings (unit_id, lower(external_ref));
CREATE INDEX bookings_unit_arrival_idx ON vacationrental.bookings (unit_id, arrival);

CREATE TABLE vacationrental.statements
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_id        BIGINT       NOT NULL,
    provider       VARCHAR(128) NOT NULL,
    period_from    DATE         NOT NULL,
    period_to      DATE         NOT NULL CHECK (period_to >= period_from),
    statement_date DATE         NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX statements_unit_idx ON vacationrental.statements (unit_id, statement_date);

-- One line per booking: a booking is settled on at most one statement.
CREATE TABLE vacationrental.statement_lines
(
    booking_id   BIGINT PRIMARY KEY REFERENCES vacationrental.bookings (id),
    statement_id BIGINT         NOT NULL REFERENCES vacationrental.statements (id) ON DELETE CASCADE,
    gross        NUMERIC(12, 2) NOT NULL,
    commission   NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (commission >= 0),
    cleaning     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cleaning >= 0)
);

CREATE INDEX statement_lines_statement_idx ON vacationrental.statement_lines (statement_id);
