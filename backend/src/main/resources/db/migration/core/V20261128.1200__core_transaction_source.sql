-- Transactions created and managed by a module, e.g. the transaction of a rent payment (change rent-payments).
-- Existing transactions keep no source and stay editable.
ALTER TABLE core.transactions
    ADD COLUMN source_type VARCHAR(64),
    ADD COLUMN source_id   BIGINT,
    ADD CONSTRAINT transactions_source_check CHECK ((source_type IS NULL) = (source_id IS NULL));

CREATE INDEX transactions_source_idx ON core.transactions (source_type, source_id) WHERE source_type IS NOT NULL;
