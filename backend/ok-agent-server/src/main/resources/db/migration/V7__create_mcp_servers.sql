CREATE TABLE mcp_server (
  id BINARY(16) NOT NULL,
  server_key VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1024) NOT NULL DEFAULT '',
  transport VARCHAR(32) NOT NULL,
  server_url VARCHAR(2048) NULL,
  command_text VARCHAR(512) NULL,
  arguments_json TEXT NOT NULL,
  query_parameters_json TEXT NOT NULL,
  secrets_ciphertext TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  request_timeout_seconds INT NOT NULL DEFAULT 15,
  initialization_timeout_seconds INT NOT NULL DEFAULT 10,
  last_test_status VARCHAR(32) NOT NULL DEFAULT 'UNTESTED',
  last_tested_at TIMESTAMP(6) NULL,
  tool_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mcp_server_key (server_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mcp_tool_snapshot (
  id BINARY(16) NOT NULL,
  mcp_server_id BINARY(16) NOT NULL,
  tool_name VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  input_schema_json MEDIUMTEXT NOT NULL,
  discovered_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mcp_server_tool (mcp_server_id, tool_name),
  CONSTRAINT fk_mcp_tool_server FOREIGN KEY (mcp_server_id) REFERENCES mcp_server(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
