-- Agencies: one shared, managed list instead of the free-text agency and the fixed channel of a booking.
-- Every existing value is carried over; the old columns stay (unused) so nothing is lost.

CREATE TABLE vacationrental.agencies
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    archived   BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX agencies_name_uq ON vacationrental.agencies (lower(name));

-- Names under which mails refer to an agency; the agency's own name is always one of them.
CREATE TABLE vacationrental.agency_aliases
(
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id BIGINT       NOT NULL REFERENCES vacationrental.agencies (id) ON DELETE CASCADE,
    alias     VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX agency_aliases_uq ON vacationrental.agency_aliases (lower(alias));
CREATE INDEX agency_aliases_agency_idx ON vacationrental.agency_aliases (agency_id);

-- 1. One agency per name used so far (case-insensitive); the spelling with the most bookings wins.
WITH used AS (SELECT btrim(agency) AS name, 1 AS weight
              FROM vacationrental.bookings
              WHERE btrim(coalesce(agency, '')) <> ''
              UNION ALL
              SELECT btrim(agency), 0
              FROM vacationrental.unit_mappings
              WHERE btrim(agency) <> ''),
     ranked AS (SELECT name,
                       row_number() OVER (PARTITION BY lower(name) ORDER BY sum(weight) DESC, name) AS rn
                FROM used
                GROUP BY name)
INSERT INTO vacationrental.agencies (name)
SELECT name FROM ranked WHERE rn = 1;

-- 2. Agencies for bookings that only had a channel.
INSERT INTO vacationrental.agencies (name)
SELECT DISTINCT CASE channel
                    WHEN 'AIRBNB' THEN 'Airbnb'
                    WHEN 'BOOKING_COM' THEN 'Booking.com'
                    WHEN 'DIRECT' THEN 'Direkt'
                    ELSE 'Sonstige' END
FROM vacationrental.bookings
WHERE btrim(coalesce(agency, '')) = ''
  AND channel IS NOT NULL
ON CONFLICT (lower(name)) DO NOTHING;

-- 3. Every agency's name and every spelling used so far become mail names.
INSERT INTO vacationrental.agency_aliases (agency_id, alias)
SELECT id, name FROM vacationrental.agencies;

INSERT INTO vacationrental.agency_aliases (agency_id, alias)
SELECT DISTINCT ON (lower(u.name)) a.id, u.name
FROM (SELECT btrim(agency) AS name FROM vacationrental.bookings WHERE btrim(coalesce(agency, '')) <> ''
      UNION
      SELECT btrim(agency) FROM vacationrental.unit_mappings WHERE btrim(agency) <> '') u
         JOIN vacationrental.agencies a ON lower(a.name) = lower(u.name)
ON CONFLICT (lower(alias)) DO NOTHING;

-- 4. References. The agency wins over the channel; the channel value stays in its old column.
ALTER TABLE vacationrental.bookings
    ADD COLUMN agency_id BIGINT REFERENCES vacationrental.agencies (id);

UPDATE vacationrental.bookings b
SET agency_id = a.id
FROM vacationrental.agencies a
WHERE btrim(coalesce(b.agency, '')) <> ''
  AND lower(a.name) = lower(btrim(b.agency));

UPDATE vacationrental.bookings b
SET agency_id = a.id
FROM vacationrental.agencies a
WHERE b.agency_id IS NULL
  AND b.channel IS NOT NULL
  AND lower(a.name) = lower(CASE b.channel
                                WHEN 'AIRBNB' THEN 'Airbnb'
                                WHEN 'BOOKING_COM' THEN 'Booking.com'
                                WHEN 'DIRECT' THEN 'Direkt'
                                ELSE 'Sonstige' END);

CREATE INDEX bookings_agency_idx ON vacationrental.bookings (agency_id);

ALTER TABLE vacationrental.unit_mappings
    ADD COLUMN agency_id BIGINT REFERENCES vacationrental.agencies (id);

UPDATE vacationrental.unit_mappings m
SET agency_id = a.id
FROM vacationrental.agencies a
WHERE lower(a.name) = lower(btrim(m.agency));

-- 5. Uniqueness by agency identity instead of name; the old text columns are no longer written.
DROP INDEX vacationrental.bookings_external_ref_uq;
CREATE UNIQUE INDEX bookings_external_ref_uq
    ON vacationrental.bookings (unit_id, coalesce(agency_id, 0), lower(external_ref));

-- Mappings that only differed in the case of the agency name now collide: keep the newest.
DELETE FROM vacationrental.unit_mappings m
USING vacationrental.unit_mappings newer
WHERE newer.owner_user_id = m.owner_user_id
  AND newer.agency_id = m.agency_id
  AND lower(newer.property_name) = lower(m.property_name)
  AND newer.id > m.id;

DROP INDEX vacationrental.unit_mappings_uq;
CREATE UNIQUE INDEX unit_mappings_uq
    ON vacationrental.unit_mappings (owner_user_id, agency_id, lower(property_name));

ALTER TABLE vacationrental.unit_mappings
    ALTER COLUMN agency_id SET NOT NULL,
    ALTER COLUMN agency DROP NOT NULL;
