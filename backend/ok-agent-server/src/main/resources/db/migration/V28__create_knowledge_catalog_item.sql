CREATE TABLE knowledge_catalog_item (
  id BINARY(16) NOT NULL,
  source_id BINARY(16) NOT NULL,
  remote_knowledge_id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  document_count INT NOT NULL DEFAULT 0,
  word_count BIGINT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  tags_json TEXT NOT NULL,
  remote_description TEXT NOT NULL,
  description TEXT NOT NULL,
  remote_raw_json MEDIUMTEXT NOT NULL,
  metadata_status VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
  discovered_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_source_remote (source_id, remote_knowledge_id),
  CONSTRAINT fk_knowledge_catalog_source FOREIGN KEY (source_id) REFERENCES knowledge_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
