-- Add credentials and the MVP platform role to unified users.
--
-- Channel-created identities are not interactive console accounts, so their password hash remains
-- NULL. Existing console users also remain unable to sign in until an administrator initializes
-- their password. VIEWER is the least-privileged safe default for all existing rows.

ALTER TABLE app_user
  ADD COLUMN password_hash VARCHAR(100) NULL AFTER username,
  ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'VIEWER' AFTER password_hash,
  ADD COLUMN last_login_at TIMESTAMP(6) NULL AFTER enabled,
  ADD KEY idx_app_user_role (role);
