-- Kind of booking: a guest stay or the owner's own use of the unit („Eigenbelegung“).
-- Every existing booking is a guest booking through the default; nothing else changes for them.
ALTER TABLE vacationrental.bookings
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'GUEST' CHECK (kind IN ('GUEST', 'OWNER'));

-- An owner use has no guests: only guest bookings need at least one adult.
ALTER TABLE vacationrental.bookings DROP CONSTRAINT bookings_adults_check;
ALTER TABLE vacationrental.bookings ADD CONSTRAINT bookings_adults_check CHECK (kind = 'OWNER' OR adults >= 1);
