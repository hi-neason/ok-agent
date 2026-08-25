-- Turn persisted dialogue sessions into actionable inbox work items.
ALTER TABLE dialogue_session
  ADD COLUMN work_status VARCHAR(24) NOT NULL DEFAULT 'OPEN' AFTER next_turn_seq,
  ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL' AFTER work_status,
  ADD COLUMN priority_rank TINYINT NOT NULL DEFAULT 1 AFTER priority,
  ADD COLUMN assignee_account_id BINARY(16) NULL AFTER priority_rank,
  ADD COLUMN handoff_requested_at TIMESTAMP(6) NULL AFTER assignee_account_id,
  ADD COLUMN assigned_at TIMESTAMP(6) NULL AFTER handoff_requested_at,
  ADD COLUMN resolved_at TIMESTAMP(6) NULL AFTER assigned_at,
  ADD COLUMN closed_at TIMESTAMP(6) NULL AFTER resolved_at,
  ADD COLUMN work_item_updated_by BINARY(16) NULL AFTER closed_at,
  ADD COLUMN work_item_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER work_item_updated_by,
  ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0 AFTER work_item_updated_at,
  ADD KEY idx_dialogue_work_queue (work_status, priority, updated_at),
  ADD KEY idx_dialogue_assignee (assignee_account_id, work_status),
  ADD CONSTRAINT fk_dialogue_assignee FOREIGN KEY (assignee_account_id) REFERENCES app_user(id);
