CREATE TABLE intent (
  id BINARY(16) NOT NULL,
  parent_id BINARY(16) NULL,
  intent_key VARCHAR(128) NOT NULL,
  name VARCHAR(256) NOT NULL,
  description TEXT NULL,
  examples_json TEXT NULL,
  target_subagent_key VARCHAR(128) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_intent_key (intent_key),
  KEY idx_intent_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
