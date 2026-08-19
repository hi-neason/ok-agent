-- Channels are bound to a bot that may serve one or many users; the personal/enterprise
-- distinction and a fixed owner user are no longer meaningful. Runtime userId is derived
-- per inbound message from the channel sender (e.g. Feishu open_id).
ALTER TABLE channel_asset DROP COLUMN scope;
ALTER TABLE channel_asset DROP COLUMN owner_user_id;

-- Auto-discovered identities of people who talk to a channel-bound bot. One row per
-- (channel type, channel instance, external id). A nullable link to app_user reserves the
-- future ability to claim/aggregate a channel person into a system-managed account.
CREATE TABLE channel_user_identity (
  id BINARY(16) NOT NULL,
  channel_type VARCHAR(32) NOT NULL,
  channel_key VARCHAR(64) NOT NULL,
  external_id VARCHAR(128) NOT NULL,
  union_id VARCHAR(128) NULL,
  tenant_key VARCHAR(128) NULL,
  display_name VARCHAR(256) NULL,
  avatar_url VARCHAR(512) NULL,
  linked_user_id BINARY(16) NULL,
  first_seen_at TIMESTAMP(6) NOT NULL,
  last_seen_at TIMESTAMP(6) NOT NULL,
  last_message_at TIMESTAMP(6) NOT NULL,
  message_count BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_external (channel_type, channel_key, external_id),
  KEY idx_channel_user_channel (channel_type, channel_key),
  KEY idx_channel_user_linked (linked_user_id),
  CONSTRAINT fk_channel_user_linked FOREIGN KEY (linked_user_id) REFERENCES app_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
