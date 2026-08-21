CREATE TABLE agent_product_binding (
  id BINARY(16) NOT NULL,
  agent_id BINARY(16) NOT NULL,
  scope VARCHAR(32) NOT NULL DEFAULT 'ALL',
  scope_value TEXT NULL,
  capabilities_json TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_product_binding (agent_id),
  CONSTRAINT fk_apb_agent FOREIGN KEY (agent_id) REFERENCES agent_asset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
