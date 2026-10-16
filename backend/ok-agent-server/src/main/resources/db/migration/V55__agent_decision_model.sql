ALTER TABLE agent_asset ADD COLUMN decision_model_asset_id BINARY(16) NULL;
CREATE INDEX idx_agent_decision_model ON agent_asset (decision_model_asset_id);
