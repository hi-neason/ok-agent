-- Backfill: reassign runtime data keyed by the raw provider external id (e.g. Feishu open_id)
-- to the one-user-id that was provisioned for that channel identity in V36.
--
-- Before V36 the channel runtime used the provider open_id directly as the runtime user_id, so
-- dialogue/state/trace/persona rows for existing channel conversations live under the open_id.
-- V36 provisioned a source=CHANNEL app_user per identity; here we rewrite all those rows to the
-- new app_user.user_id so history is attached to the unified person.

-- dialogue_session: user_id is nullable and holds the runtime user string.
UPDATE dialogue_session ds
JOIN channel_user_identity ci ON ci.external_id = ds.user_id
JOIN app_user u ON u.id = ci.linked_user_id
SET ds.user_id = u.user_id;

-- agent_state: primary key starts with user_id; duplicate slots under the target are dropped.
DELETE FROM agent_state WHERE (user_id, session_id, state_key, item_index) IN (
  SELECT user_id, session_id, state_key, item_index FROM (
    SELECT a.* FROM agent_state a
    JOIN channel_user_identity ci ON ci.external_id = a.user_id
    JOIN app_user u ON u.id = ci.linked_user_id
    WHERE EXISTS (
      SELECT 1 FROM agent_state b
      WHERE b.user_id = u.user_id
        AND b.session_id = a.session_id
        AND b.state_key = a.state_key
        AND b.item_index = a.item_index
    )
  ) AS del_a
);
UPDATE agent_state a
JOIN channel_user_identity ci ON ci.external_id = a.user_id
JOIN app_user u ON u.id = ci.linked_user_id
SET a.user_id = u.user_id;

-- trace_span & workflow audit: nullable user_id, no unique constraints.
UPDATE trace_span ts
JOIN channel_user_identity ci ON ci.external_id = ts.user_id
JOIN app_user u ON u.id = ci.linked_user_id
SET ts.user_id = u.user_id;

UPDATE workflow_execution_audit w
JOIN channel_user_identity ci ON ci.external_id = w.user_id
JOIN app_user u ON u.id = ci.linked_user_id
SET w.user_id = u.user_id;

-- user_persona: PK (user_id, agent_id); drop duplicates then reassign.
DELETE FROM user_persona WHERE (user_id, agent_id) IN (
  SELECT user_id, agent_id FROM (
    SELECT p.* FROM user_persona p
    JOIN channel_user_identity ci ON ci.external_id = p.user_id
    JOIN app_user u ON u.id = ci.linked_user_id
    WHERE EXISTS (
      SELECT 1 FROM user_persona q WHERE q.user_id = u.user_id AND q.agent_id = p.agent_id
    )
  ) AS del_p
);
UPDATE user_persona p
JOIN channel_user_identity ci ON ci.external_id = p.user_id
JOIN app_user u ON u.id = ci.linked_user_id
SET p.user_id = u.user_id;

-- persona MEMORY.md in workspace_kv, namespace 'users/{externalId}/persona' -> 'users/{userId}/persona'.
-- Drop target duplicates, then move rows.
DELETE FROM workspace_kv WHERE (namespace, item_key) IN (
  SELECT namespace, item_key FROM (
    SELECT old.* FROM workspace_kv old
    JOIN channel_user_identity ci ON old.namespace = CONCAT('users/', ci.external_id, '/persona')
    JOIN app_user u ON u.id = ci.linked_user_id
    WHERE EXISTS (
      SELECT 1 FROM workspace_kv t WHERE t.namespace = CONCAT('users/', u.user_id, '/persona')
        AND t.item_key = old.item_key
    )
  ) AS del_kv
);
UPDATE workspace_kv kv
JOIN channel_user_identity ci ON kv.namespace = CONCAT('users/', ci.external_id, '/persona')
JOIN app_user u ON u.id = ci.linked_user_id
SET kv.namespace = CONCAT('users/', u.user_id, '/persona');
