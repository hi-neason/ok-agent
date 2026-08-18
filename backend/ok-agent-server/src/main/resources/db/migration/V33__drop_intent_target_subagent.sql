-- 方案 B：意图回归纯粹业务语义，意图与子 Agent 的路由绑定挪到 agent_asset.subagents_json
-- （每个 subagent 声明自己负责的 intentKeys）。意图表不再持有 target_subagent_key。
ALTER TABLE intent DROP COLUMN target_subagent_key;
