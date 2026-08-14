ALTER TABLE skill_file
  ADD COLUMN content_sha256 CHAR(64) NULL AFTER content,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER content_sha256,
  ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;

UPDATE skill_file
SET content_sha256 = SHA2(content, 256), updated_at = created_at;

ALTER TABLE skill_file
  MODIFY content_sha256 CHAR(64) NOT NULL,
  MODIFY updated_at DATETIME(6) NOT NULL;

ALTER TABLE skill_asset
  ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0 AFTER archive_size,
  ADD COLUMN package_status VARCHAR(32) NOT NULL DEFAULT 'SYNCED' AFTER content_revision;
