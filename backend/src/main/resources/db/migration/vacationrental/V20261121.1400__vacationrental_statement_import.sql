-- Statements imported from agency mails (change statement-mails): the provider's invoice number, the payout its
-- document states, and rows of the document that are on no booking yet. Existing statements keep their data; both
-- new columns stay empty for them.
ALTER TABLE vacationrental.statements
    ADD COLUMN invoice_number VARCHAR(64),
    ADD COLUMN stated_payout  NUMERIC(12, 2);

CREATE INDEX statements_invoice_idx ON vacationrental.statements (provider, invoice_number)
    WHERE invoice_number IS NOT NULL;

CREATE TABLE vacationrental.statement_open_lines
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_id BIGINT         NOT NULL REFERENCES vacationrental.statements (id) ON DELETE CASCADE,
    guest_name   VARCHAR(128)   NOT NULL,
    arrival      DATE           NOT NULL,
    departure    DATE           NOT NULL CHECK (departure >= arrival),
    gross        NUMERIC(12, 2) NOT NULL,
    commission   NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (commission >= 0),
    note         VARCHAR(128)
);

CREATE INDEX statement_open_lines_statement_idx ON vacationrental.statement_open_lines (statement_id);
