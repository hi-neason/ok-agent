-- Immutable versioned snapshots of an Agent configuration. The runtime plane only ever
-- reads agent_version.snapshot_json through an agent_release; the editable agent_asset row
-- (draft) is never consumed by production traffic.
CREATE TABLE agent_version (
  id BINARY(16) NOT NULL,
  agent_id BINARY(16) NOT NULL,
  version_no INT NOT NULL,
  version_label VARCHAR(128) NULL,
  snapshot_json LONGTEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  parent_version_id BINARY(16) NULL,
  changelog TEXT NULL,
  created_by VARCHAR(64) NOT NULL DEFAULT 'system',
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_version_no (agent_id, version_no),
  KEY idx_agent_version_agent (agent_id, created_at),
  CONSTRAINT fk_av_agent FOREIGN KEY (agent_id) REFERENCES agent_asset(id) ON DELETE CASCADE,
  CONSTRAINT fk_av_parent FOREIGN KEY (parent_version_id) REFERENCES agent_version(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A deployment record: one agent_version promoted onto a target (currently a channel).
-- Only one PROMOTED release exists per (target_type, target_id); publishing a newer
-- version marks the previous one SUPERSEDED and rollback marks it ROLLED_BACK.
CREATE TABLE agent_release (
  id BINARY(16) NOT NULL,
  agent_id BINARY(16) NOT NULL,
  version_id BINARY(16) NOT NULL,
  version_no INT NOT NULL,
  target_type VARCHAR(16) NOT NULL DEFAULT 'CHANNEL',
  target_id BINARY(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  rollback_of_id BINARY(16) NULL,
  published_by VARCHAR(64) NOT NULL DEFAULT 'system',
  published_at TIMESTAMP(6) NOT NULL,
  superseded_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  KEY idx_release_target (target_type, target_id, status, published_at),
  KEY idx_release_agent (agent_id, published_at),
  KEY idx_release_version (version_id),
  CONSTRAINT fk_ar_agent FOREIGN KEY (agent_id) REFERENCES agent_asset(id) ON DELETE CASCADE,
  CONSTRAINT fk_ar_version FOREIGN KEY (version_id) REFERENCES agent_version(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Channels point at the release currently serving production traffic. previous_release_id
-- enables one-click rollback without reconstructing history.
ALTER TABLE channel_asset
  ADD COLUMN current_release_id BINARY(16) NULL AFTER bound_agent_id,
  ADD COLUMN previous_release_id BINARY(16) NULL AFTER current_release_id,
  ADD CONSTRAINT fk_channel_current_release FOREIGN KEY (current_release_id) REFERENCES agent_release(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_channel_previous_release FOREIGN KEY (previous_release_id) REFERENCES agent_release(id) ON DELETE SET NULL;

-- Observability: attribute every production session to the release (and version) that served it,
-- so metrics can be compared across published versions. A session is bound to one release for its
-- lifetime; a new release changes the config hash and rebuilds the session.
ALTER TABLE dialogue_session
  ADD COLUMN release_id BINARY(16) NULL AFTER agent_id,
  ADD COLUMN version_no INT NULL AFTER release_id;
