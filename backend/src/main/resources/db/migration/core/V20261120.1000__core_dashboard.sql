-- Start page tiles per user (design.md of configurable-dashboard, D1). Only an addition: users without a row see
-- the default tiles, so no data is migrated.

CREATE TABLE core.dashboards
(
    user_id    BIGINT      PRIMARY KEY REFERENCES core.users (id) ON DELETE CASCADE,
    -- [{"id": "...", "type": "core.todo", "settings": {...}}, ...]; the tile catalog lives in the frontend.
    tiles      JSONB       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
