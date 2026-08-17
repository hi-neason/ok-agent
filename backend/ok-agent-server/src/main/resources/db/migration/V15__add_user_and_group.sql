-- User management: user groups and users.
--
-- `user_group` holds logical groupings of users (e.g. ops, developers). `app_user` is the
-- platform's basic identity record. A user optionally belongs to one group (group_id). No hard
-- foreign key is declared, matching the existing convention (see agent_asset.model_asset_id),
-- so group deletion can be guarded at the service layer instead of by the database.

CREATE TABLE user_group (
  id BINARY(16) NOT NULL,
  group_key VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1024) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_group_key (group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
  id BINARY(16) NOT NULL,
  username VARCHAR(128) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  email VARCHAR(255) NULL,
  phone VARCHAR(64) NULL,
  group_id BINARY(16) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
