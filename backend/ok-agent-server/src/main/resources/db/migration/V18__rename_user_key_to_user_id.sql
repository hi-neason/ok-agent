-- Rename the HarnessAgent-facing user identifier column from user_key to user_id.
--
-- V16 introduced app_user.user_key as the stable UUID that uniquely identifies a user to the
-- HarnessAgent runtime. To match the established userId convention already used by
-- dialogue_session.user_id, agent_state.user_id, and RuntimeContext.userId, the column is renamed
-- here. V17 (which seeds the built-in DEBUG user) still targets user_key; it runs before this
-- migration, so the rename sees the seeded row correctly.

ALTER TABLE app_user
  CHANGE COLUMN user_key user_id VARCHAR(128) NOT NULL,
  DROP INDEX uk_user_user_key,
  ADD UNIQUE KEY uk_user_user_id (user_id);
