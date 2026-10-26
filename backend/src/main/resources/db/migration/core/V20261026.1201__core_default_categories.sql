-- Deutsche Grundausstattung an Kategorien (Hauptkategorie, optional Unterkategorien).
INSERT INTO core.categories (kind, name, sort_order)
VALUES ('INCOME', 'Gehalt', 10),
       ('INCOME', 'Mieteinnahmen', 20),
       ('INCOME', 'Kapitalerträge', 30),
       ('INCOME', 'Sonstige Einnahmen', 90),
       ('EXPENSE', 'Wohnen', 10),
       ('EXPENSE', 'Lebensmittel', 20),
       ('EXPENSE', 'Mobilität', 30),
       ('EXPENSE', 'Versicherungen', 40),
       ('EXPENSE', 'Steuern und Abgaben', 50),
       ('EXPENSE', 'Gebühren', 60),
       ('EXPENSE', 'Sonstige Ausgaben', 90);

INSERT INTO core.categories (kind, name, parent_id, sort_order)
SELECT 'EXPENSE', sub.name, parent.id, sub.sort_order
FROM (VALUES ('Grundsteuer', 10), ('Nebenkosten', 20), ('Instandhaltung', 30)) AS sub(name, sort_order)
         JOIN core.categories parent ON parent.kind = 'EXPENSE' AND parent.name = 'Wohnen' AND parent.parent_id IS NULL;

INSERT INTO core.categories (kind, name, parent_id, sort_order)
SELECT 'EXPENSE', sub.name, parent.id, sub.sort_order
FROM (VALUES ('Kraftstoff', 10), ('Öffentlicher Verkehr', 20)) AS sub(name, sort_order)
         JOIN core.categories parent ON parent.kind = 'EXPENSE' AND parent.name = 'Mobilität' AND parent.parent_id IS NULL;
