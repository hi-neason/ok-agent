# Optimization delivery log

## 1. Strict production release resolution

Production chat now requires an enabled, published channel matching the requested Agent.
Both web chat and IM use the same release validation. The console selects published channels.
Draft execution remains available through the Agent debug API. No database migration is needed.

Validation: backend `mvn -o test`; frontend build with Node 22.

## 2. Preserve conversation history across releases

Existing web conversations retain their original immutable release, including after runtime cache eviction or restart.
Agent instance replacement never deletes business history. Legacy sessions without release attribution require
an explicit new session; their history remains readable. No schema migration is needed.
Validation: backend `mvn -o test`, including pinned-version and missing-version regressions.
