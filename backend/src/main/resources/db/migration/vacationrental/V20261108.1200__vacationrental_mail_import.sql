-- Booking mails: unit mappings per agency (mail inbox itself lives in the core).
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
