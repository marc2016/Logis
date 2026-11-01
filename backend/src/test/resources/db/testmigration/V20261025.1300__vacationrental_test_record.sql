-- Tests only: table in the module schema to prove "data is kept".
CREATE TABLE vacationrental.test_record
(
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label VARCHAR(64) NOT NULL
);
