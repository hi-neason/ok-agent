ALTER TABLE agent_asset
    ADD COLUMN mcp_tool_filters_json TEXT NOT NULL,
    ADD COLUMN memory_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN memory_flush_mode VARCHAR(32) NOT NULL DEFAULT 'THROTTLED',
    ADD COLUMN memory_flush_interval_minutes INT NOT NULL DEFAULT 30,
    ADD COLUMN memory_consolidation_interval_minutes INT NOT NULL DEFAULT 30,
    ADD COLUMN memory_daily_retention_days INT NOT NULL DEFAULT 90,
    ADD COLUMN memory_session_retention_days INT NOT NULL DEFAULT 180,
    ADD COLUMN workspace_mode VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN workspace_isolation_scope VARCHAR(16) NOT NULL DEFAULT 'SESSION',
    ADD COLUMN workspace_context_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN shell_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN docker_image VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN sandbox_memory_mb INT NOT NULL DEFAULT 512,
    ADD COLUMN sandbox_cpu_count INT NOT NULL DEFAULT 1;

UPDATE agent_asset SET mcp_tool_filters_json = '{}';
