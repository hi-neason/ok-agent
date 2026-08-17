-- MySQL-backed key/value store backing the harness BaseStore.
--
-- The harness long-term memory pipeline (flush -> memory/YYYY-MM-DD.md, consolidation -> MEMORY.md)
-- writes through WorkspaceManager into the workspace filesystem, which is mounted on a BaseStore.
-- By implementing BaseStore over MySQL, the agent's long-term memory (and any other routed
-- workspace paths) becomes durable and survives JVM restarts, instead of living on local disk.
--
-- Items are addressed by (namespace, item_key). The harness encodes the agent/route hierarchy in
-- the namespace tuple (e.g. ["agents", "<agentKey>", "memory"]) and stores the file path as the key.

CREATE TABLE workspace_kv (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  namespace  VARCHAR(255) NOT NULL,
  item_key   VARCHAR(384) NOT NULL,
  value_json LONGTEXT     NOT NULL,
  version    BIGINT       NOT NULL DEFAULT 1,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_workspace_kv_ns_key (namespace, item_key),
  KEY idx_workspace_kv_namespace (namespace)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
