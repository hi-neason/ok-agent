-- Per-agent user-persona injection configuration: whether to inject the target user's persona
-- into the system prompt, and the template used to render that block.
ALTER TABLE agent_asset
    ADD COLUMN persona_memory_enabled TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN persona_prompt_template LONGTEXT;
