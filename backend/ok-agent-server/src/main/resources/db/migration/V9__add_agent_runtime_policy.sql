ALTER TABLE agent_asset
  ADD COLUMN max_iters INT NOT NULL DEFAULT 10 AFTER max_tokens,
  ADD COLUMN model_timeout_seconds INT NOT NULL DEFAULT 120 AFTER max_iters,
  ADD COLUMN tool_timeout_seconds INT NOT NULL DEFAULT 60 AFTER model_timeout_seconds,
  ADD COLUMN max_retries INT NOT NULL DEFAULT 2 AFTER tool_timeout_seconds,
  ADD COLUMN permission_mode VARCHAR(32) NOT NULL DEFAULT 'BYPASS' AFTER max_retries,
  ADD COLUMN parallel_tool_calls BOOLEAN NOT NULL DEFAULT TRUE AFTER permission_mode,
  ADD COLUMN compaction_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER parallel_tool_calls,
  ADD COLUMN max_context_tokens INT NOT NULL DEFAULT 8000 AFTER compaction_enabled,
  ADD COLUMN tool_result_eviction_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER max_context_tokens,
  ADD COLUMN tracing_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER tool_result_eviction_enabled;
