ALTER TABLE dialogue_session
  ADD COLUMN next_turn_seq INT NOT NULL DEFAULT 1 AFTER updated_at;

-- Repair any duplicate/gapped legacy sequence values before enforcing uniqueness.
UPDATE dialogue_turn t
JOIN (
  SELECT numbered.id, numbered.repaired_seq
  FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY seq, id) AS repaired_seq
    FROM dialogue_turn
  ) numbered
) ranked ON ranked.id = t.id
SET t.seq = ranked.repaired_seq;

UPDATE dialogue_session s
LEFT JOIN (
  SELECT session_id, COALESCE(MAX(seq), 0) + 1 AS next_seq
  FROM dialogue_turn
  GROUP BY session_id
) turns ON turns.session_id = s.session_id
SET s.next_turn_seq = COALESCE(turns.next_seq, 1);

ALTER TABLE dialogue_turn
  ADD UNIQUE KEY uk_dialogue_turn_session_seq (session_id, seq);
