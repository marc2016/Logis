-- Real estate core: properties, units and attributing transactions to them.

CREATE TABLE core.properties
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    legal_entity_id BIGINT       NOT NULL REFERENCES core.legal_entities (id),
    name            VARCHAR(128) NOT NULL,
    street          VARCHAR(128),
    postal_code     VARCHAR(16),
    city            VARCHAR(128),
    -- Only drives the interface: single-unit properties hide their unit level.
    multi_unit      BOOLEAN      NOT NULL DEFAULT FALSE,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX properties_legal_entity_idx ON core.properties (legal_entity_id);

CREATE TABLE core.units
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    property_id BIGINT       NOT NULL REFERENCES core.properties (id),
    name        VARCHAR(128) NOT NULL,
    usage       VARCHAR(16)  NOT NULL
        CHECK (usage IN ('VACATION_RENTAL', 'LONG_TERM_RENTAL', 'OWN_USE', 'OTHER')),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX units_name_uq ON core.units (property_id, lower(name));

-- A transaction has at most one target: a property or one of its units. Transfers have none.
ALTER TABLE core.transactions
    ADD COLUMN property_id BIGINT REFERENCES core.properties (id),
    ADD COLUMN unit_id     BIGINT REFERENCES core.units (id),
    ADD CONSTRAINT transactions_single_target CHECK (property_id IS NULL OR unit_id IS NULL),
    ADD CONSTRAINT transactions_transfer_no_target
        CHECK (transfer_id IS NULL OR (property_id IS NULL AND unit_id IS NULL));

CREATE INDEX transactions_property_idx ON core.transactions (property_id);
CREATE INDEX transactions_unit_idx ON core.transactions (unit_id);
