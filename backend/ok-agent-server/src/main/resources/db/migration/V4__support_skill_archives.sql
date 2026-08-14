ALTER TABLE skill_asset
  ADD COLUMN business_domain VARCHAR(64) NOT NULL DEFAULT 'GENERAL' AFTER description,
  ADD COLUMN archive_name VARCHAR(255) NULL AFTER business_domain,
  ADD COLUMN archive_sha256 CHAR(64) NULL AFTER archive_name,
  ADD COLUMN archive_size BIGINT NOT NULL DEFAULT 0 AFTER archive_sha256;

CREATE TABLE skill_file (
  id BINARY(16) NOT NULL,
  skill_id BINARY(16) NOT NULL,
  file_path VARCHAR(700) NOT NULL,
  media_type VARCHAR(128) NOT NULL,
  file_size BIGINT NOT NULL,
  content LONGBLOB NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_file_path (skill_id, file_path),
  KEY idx_skill_file_skill (skill_id),
  CONSTRAINT fk_skill_file_asset FOREIGN KEY (skill_id) REFERENCES skill_asset (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
