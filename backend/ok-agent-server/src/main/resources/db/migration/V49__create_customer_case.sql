-- Sales leads and support tickets traceably converted from conversations.
CREATE TABLE customer_case (
  id BINARY(16) NOT NULL,
  type VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL,
  title VARCHAR(255) NOT NULL,
  customer_user_id VARCHAR(128) NULL,
  source_session_id VARCHAR(64) NOT NULL,
  description TEXT NULL,
  priority VARCHAR(16) NOT NULL,
  owner_account_id BINARY(16) NULL,
  created_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_case_session_type (source_session_id, type),
  KEY idx_customer_case_owner_status (owner_account_id, status),
  KEY idx_customer_case_customer (customer_user_id),
  CONSTRAINT fk_customer_case_session FOREIGN KEY (source_session_id)
    REFERENCES dialogue_session(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
