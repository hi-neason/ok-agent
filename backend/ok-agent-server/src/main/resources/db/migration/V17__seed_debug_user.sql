-- Seed a built-in DEBUG user so the agent debug preview can run without picking a real user.
--
-- The fixed user_key follows the harness-facing identifier pattern (a UUID string) introduced in
-- V16. It is inserted idempotently: if a user named 'debug' already exists (e.g. from a prior seed
-- or manual test), the statement is a no-op rather than failing the migration on a duplicate key.

INSERT INTO app_user (id, user_key, username, display_name, email, phone, group_id, enabled, version, created_at, updated_at)
SELECT
  UNHEX(REPLACE('11111111-1111-1111-1111-111111111111', '-', '')),
  'deadbeef-dead-beef-dead-beefdeadbeef',
  'debug',
  'DEBUG用户',
  NULL,
  NULL,
  NULL,
  TRUE,
  0,
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'debug');
