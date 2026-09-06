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

## 5. Revoke stale account tokens

V54 adds a security version to console accounts. JWT validation checks current enabled state,
role, subject and security version on every request. Disabling, changing roles or resetting passwords
invalidates previously issued tokens across replicas. Existing tokens without the new claim require re-login.
Validation: backend tests, including disable/re-enable, downgrade and password reset.

## 6. Page customers and batch inbox lookups

The inbox now requests one database page of customer groups. Sessions for the page retain channel grouping;
anonymous sessions remain separate. Related Agent/customer/operator names are loaded in batches, and
message counts use the transactionally allocated sequence counter instead of one count query per row.
Validation: backend customer grouping/pagination integration tests, frontend tests and build.

## 6b. Bound conversation message loading

Inbox details load the latest 100 messages with an exclusive sequence cursor and an explicit older-message action.
Changing the selected session prevents an older request from writing into the new session's history.
The existing full replay API remains compatible. Validation: message-window integration tests and frontend build.

## 7. Isolate channel reconciliation from draft edits

Saving an Agent draft no longer restarts its production channels. Release/channel events continue to reconcile
runtime instances. Each channel has its own lifecycle monitor, so slow network startup does not serialize
unrelated channels. Shutdown coordinates with in-progress starts.
Validation: backend tests, including simultaneous slow/fast channel startup with bounded latches.

## 8. Freeze intent routing and reuse model transport

New versions include routing rules in their content hash. Classification uses the frozen model endpoint/name,
shared AgentScope transport, bounded retry/timeout and structured duration/usage logging. Credentials remain
resolved references. Agents without delegates skip classification. Legacy snapshots without routing rules skip
pre-classification rather than reading live mutable rules; publish a new version to enable it.
Validation: backend frozen-rule/legacy tests and existing snapshot asset regressions. No provider calls in tests.
