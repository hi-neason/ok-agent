-- Throttle marker for automatic persona extraction: the last time the LLM pipeline
-- extracted facts/memory from this user's conversations.
ALTER TABLE user_persona
    ADD COLUMN last_extracted_at TIMESTAMP(6) NULL AFTER summary;
