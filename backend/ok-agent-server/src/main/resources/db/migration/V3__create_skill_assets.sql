CREATE TABLE skill_asset (
  id BINARY(16) NOT NULL,
  skill_key VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1024) NOT NULL,
  asset_version VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_uri VARCHAR(1024) NULL,
  entry_file VARCHAR(255) NOT NULL DEFAULT 'SKILL.md',
  content MEDIUMTEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_asset_key (skill_key),
  KEY idx_skill_asset_source_enabled (source_type, enabled),
  KEY idx_skill_asset_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
