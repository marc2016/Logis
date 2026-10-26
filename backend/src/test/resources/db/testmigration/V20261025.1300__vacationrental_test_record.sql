-- Nur fuer Tests: Tabelle im Modulschema, um "Daten bleiben erhalten" nachzuweisen.
CREATE TABLE vacationrental.test_record
(
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label VARCHAR(64) NOT NULL
);
