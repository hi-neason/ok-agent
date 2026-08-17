-- 用户画像按 (用户 x Agent) 维度存储：每个 Agent 独立抽取并持有自己对该用户的画像。
-- 注入策略独立于抽取开关，支持 NONE / SELF_ONLY / GLOBAL。

-- 1) 拆分 agent_asset 上的"画像总开关"为：抽取开关 + 注入方式
ALTER TABLE agent_asset
    ADD COLUMN persona_extract_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER persona_memory_enabled,
    ADD COLUMN persona_injection_mode VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER persona_extract_enabled;

-- 旧的 persona_memory_enabled=true 同时代表"抽取"和"注入自己"，迁移语义：
UPDATE agent_asset
SET persona_extract_enabled = persona_memory_enabled,
    persona_injection_mode = CASE WHEN persona_memory_enabled = 1 THEN 'SELF_ONLY' ELSE 'NONE' END;

ALTER TABLE agent_asset DROP COLUMN persona_memory_enabled;

-- 2) user_persona 复合主键 (user_id, agent_id)
ALTER TABLE user_persona ADD COLUMN agent_id BINARY(16) NULL AFTER user_id;

-- 把存量行（由开启抽取的 Agent 产生）回填到该 Agent；若有多个则取最早创建的一个。
UPDATE user_persona up
SET up.agent_id = (
    SELECT aa.id FROM agent_asset aa
    WHERE aa.persona_extract_enabled = 1
    ORDER BY aa.created_at ASC LIMIT 1
)
WHERE up.agent_id IS NULL;

-- 对没有任何开启抽取 Agent 的极端情况，兜底删除（否则无法加非空约束）。
DELETE FROM user_persona WHERE agent_id IS NULL;

ALTER TABLE user_persona MODIFY COLUMN agent_id BINARY(16) NOT NULL;
ALTER TABLE user_persona DROP PRIMARY KEY, ADD PRIMARY KEY (user_id, agent_id);
