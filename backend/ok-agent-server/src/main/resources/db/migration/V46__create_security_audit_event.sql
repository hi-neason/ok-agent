-- Append-only audit trail for authentication and authorization administration.

CREATE TABLE security_audit_event (
  id BINARY(16) NOT NULL,
  actor_id BINARY(16) NULL,
  actor_username VARCHAR(128) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  details VARCHAR(1024) NOT NULL DEFAULT '',
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_security_audit_occurred (occurred_at),
  KEY idx_security_audit_actor (actor_id, occurred_at),
  KEY idx_security_audit_target (target_type, target_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
