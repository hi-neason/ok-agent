-- Add the HarnessAgent-facing user identifier (user_key) to app_user.
--
-- Mirrors agent_asset.agent_key: a stable UUID string that uniquely identifies a user to the
-- HarnessAgent runtime. Unlike the internal @Id (BINARY(16) PK), this is the logical handle
-- harness code uses to reference a user. It is generated once at user creation and never changed.

ALTER TABLE app_user
  ADD COLUMN user_key VARCHAR(128) NOT NULL,
  ADD UNIQUE KEY uk_user_user_key (user_key);
