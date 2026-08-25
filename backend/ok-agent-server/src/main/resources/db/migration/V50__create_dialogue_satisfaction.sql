-- Five-point customer satisfaction feedback for completed conversations.
CREATE TABLE dialogue_satisfaction (
  session_id VARCHAR(64) NOT NULL,
  rating INT NOT NULL,
  feedback VARCHAR(1000) NULL,
  updated_by BINARY(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (session_id),
  KEY idx_dialogue_satisfaction_rating (rating),
  CONSTRAINT fk_dialogue_satisfaction_session FOREIGN KEY (session_id)
    REFERENCES dialogue_session(session_id) ON DELETE CASCADE,
  CONSTRAINT chk_dialogue_satisfaction_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
