-- Admin export of pseudonymized mails with the owner's consent. Only additions: nobody has consented yet.

ALTER TABLE core.mailboxes ADD COLUMN admin_export_allowed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE core.mailboxes ADD COLUMN admin_export_allowed_at TIMESTAMPTZ;

-- One row per exported mailbox; never mail content.
CREATE TABLE core.mail_exports
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id BIGINT      NOT NULL REFERENCES core.users (id),
    owner_user_id BIGINT      NOT NULL REFERENCES core.users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    mail_count    INT         NOT NULL,
    filters       JSONB       NOT NULL
);

CREATE INDEX mail_exports_owner_idx ON core.mail_exports (owner_user_id, created_at DESC);
