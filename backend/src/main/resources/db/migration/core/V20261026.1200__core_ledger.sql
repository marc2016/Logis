-- Finanzkern: Freigaben, Rechtstraeger, Konten, Kategorien, Transaktionen.

-- Freigaben fuer beliebige Objekttypen (object_type z. B. 'account').
CREATE TABLE core.object_shares
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_type VARCHAR(64) NOT NULL,
    object_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL REFERENCES core.users (id),
    role        VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT object_shares_uq UNIQUE (object_type, object_id, user_id)
);

CREATE INDEX object_shares_user_idx ON core.object_shares (user_id, object_type);

-- Rechtstraeger (Eigentuemer); geben selbst keine Rechte.
CREATE TABLE core.legal_entities
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    kind            VARCHAR(16)  NOT NULL CHECK (kind IN ('PRIVATE_PERSON', 'GBR', 'GMBH', 'OTHER')),
    tax_number      VARCHAR(32),
    manager_user_id BIGINT       NOT NULL REFERENCES core.users (id),
    personal        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (NOT personal OR kind = 'PRIVATE_PERSON')
);

CREATE UNIQUE INDEX legal_entities_name_uq ON core.legal_entities (manager_user_id, lower(name));
-- Genau ein privater Rechtstraeger je Benutzer.
CREATE UNIQUE INDEX legal_entities_personal_uq ON core.legal_entities (manager_user_id) WHERE personal;

CREATE TABLE core.accounts
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    legal_entity_id BIGINT         NOT NULL REFERENCES core.legal_entities (id),
    name            VARCHAR(128)   NOT NULL,
    kind            VARCHAR(16)    NOT NULL CHECK (kind IN ('CHECKING', 'SAVINGS', 'CASH', 'CREDIT_CARD', 'OTHER')),
    currency        CHAR(3)        NOT NULL DEFAULT 'EUR' CHECK (currency = 'EUR'),
    iban            VARCHAR(34),
    opening_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
    archived        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX accounts_legal_entity_idx ON core.accounts (legal_entity_id);

-- Gemeinsamer Kategorienbaum (max. zwei Ebenen, im Service geprueft).
CREATE TABLE core.categories
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id  BIGINT REFERENCES core.categories (id),
    kind       VARCHAR(16)  NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    name       VARCHAR(64)  NOT NULL,
    -- Von einem Modul bereitgestellt: stabiler Schluessel und Modul-ID.
    module_key VARCHAR(128) UNIQUE,
    module_id  VARCHAR(64),
    archived   BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    CHECK ((module_key IS NULL) = (module_id IS NULL))
);

CREATE UNIQUE INDEX categories_name_uq ON core.categories (kind, COALESCE(parent_id, 0), lower(name));
CREATE INDEX categories_parent_idx ON core.categories (parent_id);

CREATE TABLE core.transactions
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT         NOT NULL REFERENCES core.accounts (id),
    booking_date DATE           NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL CHECK (amount <> 0),
    category_id  BIGINT REFERENCES core.categories (id),
    counterparty VARCHAR(255),
    purpose      VARCHAR(500),
    note         TEXT,
    -- Beide Seiten einer Umbuchung teilen sich die transfer_id.
    transfer_id  UUID,
    version      INTEGER        NOT NULL DEFAULT 0,
    created_by   BIGINT         NOT NULL REFERENCES core.users (id),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by   BIGINT         NOT NULL REFERENCES core.users (id),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (transfer_id IS NULL OR category_id IS NULL)
);

CREATE INDEX transactions_account_date_idx ON core.transactions (account_id, booking_date DESC);
CREATE INDEX transactions_transfer_idx ON core.transactions (transfer_id);
CREATE INDEX transactions_category_idx ON core.transactions (category_id);
