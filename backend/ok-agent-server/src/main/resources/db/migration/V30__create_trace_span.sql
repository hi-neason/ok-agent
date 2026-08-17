-- Full-fidelity execution trace (spans) for observability.
--
-- One user question + agent answer (a "turn") is represented as one trace: a root AGENT span
-- with child MODEL (LLM call) and TOOL (MCP / knowledge / workflow / built-in) spans. Spans are
-- captured by a framework middleware straight from the ReAct execution core, so knowledge-base and
-- workflow tools -- which are standard @Tool methods -- are included automatically.
--
-- Storage is intentionally self-contained (no external APM / OTLP collector): the same MySQL
-- instance backs both the conversation history and its traces. Span attributes / input / output
-- are stored verbatim (LONGTEXT) for full replay; retention/retrieval policy is enforced by the
-- application layer.

CREATE TABLE trace_span (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  trace_id        VARCHAR(64)  NOT NULL,
  span_id         VARCHAR(64)  NOT NULL,
  parent_span_id  VARCHAR(64)  NULL,
  session_id      VARCHAR(64)  NOT NULL,
  agent_id        BINARY(16)   NULL,
  user_id         VARCHAR(128) NULL,
  turn_seq        INT          NOT NULL,
  span_type       VARCHAR(16)  NOT NULL,
  name            VARCHAR(255) NOT NULL,
  start_us        BIGINT       NOT NULL,
  end_us          BIGINT       NOT NULL,
  duration_us     BIGINT       NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  attributes      JSON         NULL,
  input           LONGTEXT     NULL,
  output          LONGTEXT     NULL,
  created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_trace_span (trace_id, span_id),
  KEY idx_trace_span_session_turn (session_id, turn_seq),
  KEY idx_trace_span_trace (trace_id),
  KEY idx_trace_span_parent (parent_span_id),
  KEY idx_trace_span_agent_start (agent_id, start_us),
  KEY idx_trace_span_type (span_type),
  KEY idx_trace_span_start (start_us)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Link each persisted dialogue turn to its trace so the UI can expand a turn into the span tree.
ALTER TABLE dialogue_turn ADD COLUMN trace_id VARCHAR(64) NULL AFTER token_usage;
ALTER TABLE dialogue_turn ADD KEY idx_dialogue_turn_trace (trace_id);
