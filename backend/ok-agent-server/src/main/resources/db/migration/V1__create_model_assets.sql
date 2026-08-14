CREATE TABLE model_asset (
  id BINARY(16) NOT NULL,
  name VARCHAR(128) NOT NULL,
  type VARCHAR(32) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model_id VARCHAR(128) NOT NULL,
  endpoint VARCHAR(1024) NOT NULL,
  secret_ref VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_model_asset_type_enabled (type, enabled),
  KEY idx_model_asset_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
