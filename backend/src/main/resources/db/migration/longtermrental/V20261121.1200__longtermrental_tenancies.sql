-- Long-term tenancies (design.md of long-term-rental, D2). Only new tables in a new schema.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- unit_id refers to core.units without a foreign key: the core asks the module before deleting (UnitReferences).
CREATE TABLE longtermrental.tenancies
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_id             BIGINT        NOT NULL,
    -- Only residential tenancies for now; commercial ones extend this check.
    kind                VARCHAR(16)   NOT NULL DEFAULT 'RESIDENTIAL' CHECK (kind IN ('RESIDENTIAL')),
    start_date          DATE          NOT NULL,
    -- The last day of the tenancy; NULL while it runs indefinitely.
    end_date            DATE,
    deposit_amount      NUMERIC(12, 2) CHECK (deposit_amount >= 0),
    deposit_received_on DATE,
    tenants_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT tenancies_period CHECK (end_date IS NULL OR end_date >= start_date),
    -- At most one tenancy per unit at any time; an open end blocks every later tenancy.
    CONSTRAINT tenancies_no_overlap EXCLUDE USING gist (unit_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
);

CREATE INDEX tenancies_unit_idx ON longtermrental.tenancies (unit_id);

-- Name and contact data are NULL after „Mieterdaten löschen“.
CREATE TABLE longtermrental.tenants
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenancy_id  BIGINT       NOT NULL REFERENCES longtermrental.tenancies (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL DEFAULT 0,
    name        VARCHAR(200),
    street      VARCHAR(200),
    postal_code VARCHAR(16),
    city        VARCHAR(128),
    country     VARCHAR(64),
    phone       VARCHAR(64),
    email       VARCHAR(254)
);

CREATE INDEX tenants_tenancy_idx ON longtermrental.tenants (tenancy_id);

-- The rent as a history: one term per „valid from“ date.
CREATE TABLE longtermrental.rent_terms
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenancy_id BIGINT NOT NULL REFERENCES longtermrental.tenancies (id) ON DELETE CASCADE,
    valid_from DATE   NOT NULL,
    CONSTRAINT rent_terms_date_uq UNIQUE (tenancy_id, valid_from)
);

-- Amounts are net with a tax rate, which is 0 for residential tenancies.
CREATE TABLE longtermrental.rent_items
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rent_term_id BIGINT         NOT NULL REFERENCES longtermrental.rent_terms (id) ON DELETE CASCADE,
    kind         VARCHAR(24)    NOT NULL CHECK (kind IN ('COLD_RENT', 'UTILITIES_ADVANCE', 'OTHER')),
    -- Only for OTHER, e.g. „Stellplatz“.
    label        VARCHAR(100),
    net_amount   NUMERIC(12, 2) NOT NULL CHECK (net_amount >= 0),
    tax_rate     NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    position     INTEGER        NOT NULL DEFAULT 0
);

CREATE INDEX rent_items_term_idx ON longtermrental.rent_items (rent_term_id);
