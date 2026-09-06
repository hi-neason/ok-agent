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

## 3. Coordinate runtime cache lifecycle

A bounded lease-based pool now prevents replacement or eviction of active Agent instances.
Saturated runtimes reject new sessions, failed builds do not consume capacity, and shutdown drains active leases.
Validation: backend `mvn -o test`, including busy replacement, capacity, shutdown and construction failures.
This cache is process-local; production must use one runtime owner until distributed execution ownership is configured.

## 4. Enforce human handoff at runtime

Web, Feishu, DingTalk and WeChat persist inbound messages but only run/reply while the business
session is OPEN. Each channel checks again immediately before outbound delivery to suppress a
response generated during handoff. Explicit resume clears the human assignee; closed sessions cannot resume.
No new automated delivery is attempted during handoff; an already transmitted network request cannot be recalled.
Validation: backend tests and frontend build; live provider delivery is not exercised.
