CREATE TABLE agent_workflow_binding (
  id BINARY(16) NOT NULL,
  agent_id BINARY(16) NOT NULL,
  catalog_item_id BINARY(16) NOT NULL,
  description_override TEXT NULL,
  parameter_defaults_json TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_workflow_binding (agent_id, catalog_item_id),
  CONSTRAINT fk_awb_agent FOREIGN KEY (agent_id) REFERENCES agent_asset(id) ON DELETE CASCADE,
  CONSTRAINT fk_awb_catalog_item FOREIGN KEY (catalog_item_id) REFERENCES workflow_catalog_item(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
