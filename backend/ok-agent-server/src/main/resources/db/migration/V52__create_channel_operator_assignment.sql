-- Assign enterprise channel accounts to the human operators who may receive handoffs.
CREATE TABLE channel_operator_assignment (
  id BINARY(16) NOT NULL,
  channel_id BINARY(16) NOT NULL,
  operator_account_id BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  created_by BINARY(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_operator (channel_id, operator_account_id),
  KEY idx_operator_channel (operator_account_id, channel_id),
  CONSTRAINT fk_channel_operator_channel FOREIGN KEY (channel_id) REFERENCES channel_asset(id) ON DELETE CASCADE,
  CONSTRAINT fk_channel_operator_account FOREIGN KEY (operator_account_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operator_presence (
  operator_account_id BINARY(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
  updated_at TIMESTAMP(6) NOT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (operator_account_id),
  CONSTRAINT fk_operator_presence_account FOREIGN KEY (operator_account_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
