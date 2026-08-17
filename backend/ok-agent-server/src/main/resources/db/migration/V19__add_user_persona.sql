-- User persona: the per-user long-term memory and insight layer.
-- Distinct dimension from the agent's own memory (memory/): this stores what we know
-- ABOUT a user (tags, preferences, facts, summary) plus a free-form MEMORY.md that
-- is persisted via the shared JdbcBaseStore under the "users/{userId}/persona" namespace.
CREATE TABLE user_persona (
    user_id           VARCHAR(128) NOT NULL,
    tags_json         LONGTEXT,
    preferences_json  LONGTEXT,
    facts             LONGTEXT,
    summary           LONGTEXT,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT NOW(6),
    updated_at        TIMESTAMP(6) NOT NULL DEFAULT NOW(6),
    PRIMARY KEY (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
