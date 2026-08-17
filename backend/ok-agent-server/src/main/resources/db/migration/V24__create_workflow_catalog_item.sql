CREATE TABLE workflow_catalog_item (
  id BINARY(16) NOT NULL,
  source_id BINARY(16) NOT NULL,
  remote_workflow_id VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  remote_mode VARCHAR(32) NOT NULL DEFAULT '',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  tags_json TEXT NOT NULL,
  remote_description TEXT NOT NULL,
  description TEXT NOT NULL,
  input_schema_json MEDIUMTEXT NOT NULL,
  remote_raw_json MEDIUMTEXT NOT NULL,
  metadata_status VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
  discovered_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_workflow_source_remote (source_id, remote_workflow_id),
  CONSTRAINT fk_workflow_catalog_source FOREIGN KEY (source_id) REFERENCES workflow_source(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
