-- Jeder bestehende Benutzer erhaelt seinen privaten Rechtstraeger.
-- Neue Benutzer bekommen ihn ueber das UserCreatedEvent.
INSERT INTO core.legal_entities (name, kind, manager_user_id, personal)
SELECT u.display_name || ' privat', 'PRIVATE_PERSON', u.id, TRUE
FROM core.users u
WHERE NOT EXISTS (SELECT 1 FROM core.legal_entities le WHERE le.manager_user_id = u.id AND le.personal);
