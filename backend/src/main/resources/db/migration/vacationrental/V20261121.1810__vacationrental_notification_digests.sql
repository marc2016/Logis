-- One row per day on which the arrival and departure digests were sent, so a restart or a second instance never
-- sends them twice (design D9 of notifications-pushover). Only an addition.

CREATE TABLE vacationrental.notification_digests
(
    day     DATE        PRIMARY KEY,
    sent_at TIMESTAMPTZ NOT NULL
);
