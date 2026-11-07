-- Calendar subscriptions: secret links to a read-only iCalendar feed of one unit.
CREATE TABLE vacationrental.calendar_links
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_id     BIGINT       NOT NULL,
    created_by  BIGINT       NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    with_guests BOOLEAN      NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX calendar_links_unit_idx ON vacationrental.calendar_links (unit_id, created_by);
