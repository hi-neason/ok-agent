-- WeChat iLink (ClawBot) per-channel login session.
-- One row per WECHAT channel; holds the QR login flow state, the (encrypted)
-- bot_token obtained after scan confirmation, and the long-polling cursor.
-- Separated from channel_asset because these fields change frequently (polling,
-- cursor advancement) and must not fight channel_asset's optimistic lock.
CREATE TABLE channel_ilink_session (
  channel_id BINARY(16) NOT NULL,
  login_status VARCHAR(16) NOT NULL DEFAULT 'LOGGED_OUT',
  bot_token_ciphertext TEXT NULL,
  bot_id VARCHAR(128) NULL,
  ilink_user_id VARCHAR(128) NULL,
  qrcode_token VARCHAR(255) NULL,
  qrcode_url VARCHAR(512) NULL,
  poll_cursor VARCHAR(512) NOT NULL DEFAULT '',
  last_error TEXT NULL,
  logged_in_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (channel_id),
  CONSTRAINT fk_ilink_channel FOREIGN KEY (channel_id) REFERENCES channel_asset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
