-- One-user-id: promote auto-discovered channel identities into first-class app_user rows.
--
-- app_user becomes the unified "person" principal. Existing console accounts keep source=CONSOLE.
-- Each channel_user_identity without a linked_user_id gets its own source=CHANNEL app_user; the
-- runtime then keys persona/memory/dialogue by app_user.user_id instead of the raw provider
-- open_id. Cross-channel identity merging is a separate, later operation (merge API).

ALTER TABLE app_user
  ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'CONSOLE' AFTER user_id,
  ADD COLUMN avatar_url VARCHAR(512) NULL AFTER display_name;

-- Backfill: one app_user per unbound identity. The username is a deterministic placeholder
-- ('ch:' + hex(identity id)) so the following UPDATE can join it back to set linked_user_id.
INSERT INTO app_user
  (id, user_id, username, source, display_name, avatar_url, email, phone, group_id,
   enabled, version, created_at, updated_at)
SELECT
  UUID_TO_BIN(UUID()),
  UUID(),
  CONCAT('ch:', HEX(ci.id)),
  'CHANNEL',
  COALESCE(NULLIF(ci.display_name, ''), CONCAT(LOWER(ci.channel_type), '-user')),
  ci.avatar_url,
  NULL, NULL, NULL, TRUE, 0,
  ci.created_at, NOW(6)
FROM channel_user_identity ci
WHERE ci.linked_user_id IS NULL;

UPDATE channel_user_identity ci
JOIN app_user u ON u.username = CONCAT('ch:', HEX(ci.id))
SET ci.linked_user_id = u.id
WHERE ci.linked_user_id IS NULL;
