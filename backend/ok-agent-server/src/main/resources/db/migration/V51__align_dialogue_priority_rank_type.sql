-- Hibernate maps the Java Integer priority rank to MySQL INT.
ALTER TABLE dialogue_session
  MODIFY COLUMN priority_rank INT NOT NULL DEFAULT 20;
