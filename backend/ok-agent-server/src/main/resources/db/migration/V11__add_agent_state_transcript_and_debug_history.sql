-- MySQL-backed persistence for agent working memory, session transcripts, and conversation history.
-- Replaces the previous in-memory AgentStateStore / FilesystemTranscriptStore and the
-- ConcurrentHashMap debug session registry.
--
-- The conversation history tables (dialogue_session / dialogue_turn) are runtime-agnostic: both the
-- debug runtime and real runtime instances record into them through the shared DialogueService, and
-- the observability module reads from them.

CREATE TABLE agent_state (
  user_id    VARCHAR(128) NOT NULL,
  session_id VARCHAR(255) NOT NULL,
  state_key  VARCHAR(255) NOT NULL,
  item_index INT          NOT NULL DEFAULT 0,
  state_data LONGTEXT     NOT NULL,
  version    BIGINT       NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, session_id, state_key, item_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_transcript (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  tenant     VARCHAR(128) NOT NULL,
  agent_id   VARCHAR(255) NOT NULL,
  session_id VARCHAR(255) NOT NULL,
  seq_start  BIGINT       NOT NULL,
  seq_end    BIGINT       NOT NULL,
  writer_id  VARCHAR(128) NOT NULL,
  payload    LONGBLOB     NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_transcript_ref (tenant, agent_id, session_id, seq_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dialogue_session (
  session_id VARCHAR(64)  NOT NULL,
  agent_id   BINARY(16)   NOT NULL,
  title      VARCHAR(255) NOT NULL DEFAULT '',
  user_id    VARCHAR(128) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (session_id),
  KEY idx_dialogue_session_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dialogue_turn (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  session_id VARCHAR(64)  NOT NULL,
  seq        INT          NOT NULL,
  role       VARCHAR(16)  NOT NULL,
  content    MEDIUMTEXT   NOT NULL,
  model      VARCHAR(128) NULL,
  latency_ms INT          NULL,
  token_usage INT         NULL,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_dialogue_turn_session (session_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
