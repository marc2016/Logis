-- Rent payments of tenancies (change rent-payments). Each payment has a transaction in the ledger that it manages;
-- the link is kept by the application, as modules own their schemas.
CREATE TABLE longtermrental.rent_payments
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenancy_id     BIGINT         NOT NULL REFERENCES longtermrental.tenancies (id) ON DELETE RESTRICT,
    payment_date   DATE           NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    transaction_id BIGINT         UNIQUE, -- set in the same database transaction right after the insert
    note           VARCHAR(500),
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX rent_payments_tenancy_idx ON longtermrental.rent_payments (tenancy_id, payment_date);
