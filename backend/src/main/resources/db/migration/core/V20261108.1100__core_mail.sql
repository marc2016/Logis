-- Mail inbox: one IMAP mailbox per user and the fetched mails with what a handler did with them.
CREATE TABLE core.mailboxes
(
    owner_user_id BIGINT PRIMARY KEY REFERENCES core.users (id),
    host          VARCHAR(255) NOT NULL,
    port          INTEGER      NOT NULL CHECK (port BETWEEN 1 AND 65535),
    username      VARCHAR(255) NOT NULL,
    -- Encrypted with SecretCipher; never returned by the API.
    password_enc  TEXT         NOT NULL,
    folder        VARCHAR(255) NOT NULL DEFAULT 'INBOX',
    use_tls       BOOLEAN      NOT NULL DEFAULT TRUE,
    cutoff_date   DATE         NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_poll_at  TIMESTAMPTZ,
    last_error    VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE core.mails
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL REFERENCES core.users (id),
    message_id    VARCHAR(512) NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL,
    sender        VARCHAR(255),
    subject       VARCHAR(998),
    -- Empty once the user deleted the mail; the Message-ID stays so it is not fetched again.
    raw           BYTEA        NOT NULL,
    text_preview  TEXT,
    handler       VARCHAR(64),
    result        VARCHAR(32)  NOT NULL,
    result_detail VARCHAR(255),
    kind          VARCHAR(32),
    source        VARCHAR(64),
    reference     VARCHAR(128),
    subject_name  VARCHAR(255),
    link_type     VARCHAR(64),
    link_id       BIGINT,
    data          JSONB,
    parsed_by     VARCHAR(8),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (owner_user_id, message_id)
);

CREATE INDEX mails_owner_idx ON core.mails (owner_user_id, received_at DESC);
CREATE INDEX mails_link_idx ON core.mails (link_type, link_id);
