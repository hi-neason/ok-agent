ALTER TABLE agent_asset ADD COLUMN subagents_json TEXT NULL;
UPDATE agent_asset SET subagents_json = '[]' WHERE subagents_json IS NULL;
ALTER TABLE agent_asset MODIFY COLUMN subagents_json TEXT NOT NULL;
