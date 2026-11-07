-- Files of any owner type (receipts on costs, documents on bookings, ...). Access is decided by the owner type.
CREATE TABLE core.documents
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_type   VARCHAR(64)  NOT NULL,
    owner_id     BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(64)  NOT NULL,
    size_bytes   INTEGER      NOT NULL CHECK (size_bytes > 0),
    content      BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX documents_owner_idx ON core.documents (owner_type, owner_id);

-- Costs recorded directly on a property (unit_id NULL) or one of its units, without an account.
CREATE TABLE core.property_costs
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    property_id  BIGINT         NOT NULL REFERENCES core.properties (id),
    unit_id      BIGINT REFERENCES core.units (id),
    cost_date    DATE           NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    category_id  BIGINT REFERENCES core.categories (id),
    counterparty VARCHAR(128),
    note         TEXT,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX property_costs_property_idx ON core.property_costs (property_id, cost_date);
CREATE INDEX property_costs_unit_idx ON core.property_costs (unit_id);
