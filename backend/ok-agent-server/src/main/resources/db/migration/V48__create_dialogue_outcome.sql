-- Structured sales/service result attached one-to-one to a conversation.
CREATE TABLE dialogue_outcome (
  session_id VARCHAR(64) NOT NULL,
  summary TEXT NULL,
  customer_need TEXT NULL,
  intent_label VARCHAR(128) NULL,
  product_interest VARCHAR(512) NULL,
  budget VARCHAR(128) NULL,
  purchase_timeline VARCHAR(128) NULL,
  sentiment VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
  resolution_code VARCHAR(64) NULL,
  next_action VARCHAR(512) NULL,
  follow_up_at TIMESTAMP(6) NULL,
  updated_by BINARY(16) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (session_id),
  KEY idx_dialogue_outcome_follow_up (follow_up_at),
  CONSTRAINT fk_dialogue_outcome_session FOREIGN KEY (session_id)
    REFERENCES dialogue_session(session_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
