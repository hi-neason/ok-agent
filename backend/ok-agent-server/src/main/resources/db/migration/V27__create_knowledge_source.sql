CREATE TABLE knowledge_source (
  id BINARY(16) NOT NULL,
  source_key VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  base_url VARCHAR(2048) NOT NULL,
  config_json TEXT NOT NULL,
  secrets_ciphertext TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_test_status VARCHAR(32) NOT NULL DEFAULT 'UNTESTED',
  last_test_message VARCHAR(1024) NOT NULL DEFAULT '',
  last_tested_at TIMESTAMP(6) NULL,
  last_synced_at TIMESTAMP(6) NULL,
  knowledge_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_source_key (source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
