# Modular monolith architecture

The application consists of an independent React/TypeScript frontend and Spring Boot 4 / Java 17 backend.
AgentScope dependencies remain pinned to the released version in `backend/pom.xml`.

## Domain responsibilities

- `agent`, `model`, `skill`, `mcp`, `knowledge`, `workflow`, `product`, `intent`: reusable assets and editable configuration.
- `release`: immutable version snapshots, published channel resolution and version relationships.
- `agentmanager`: management orchestration including version creation and promotion.
- `agentruntime`: production web execution through the customer-chat application port.
- `channel`: provider connections, sender identity mapping and runtime lifecycle.
- `conversation`: durable conversation history and the shared automation-state policy.
- `workbench`: human assignment, operational lifecycle, outcomes, cases and satisfaction.
- `identity`: interactive accounts, roles, authentication and security audit.
- `observe`: sanitized traces, retention and conversation replay.
- `shared`: technical API contracts and shared runtime failures.

Controllers expose `/api/v1` DTOs; applications coordinate business behavior; domain objects own state transitions;
persistence adapters own database access. `ModuleBoundaryTests` enforce dependency restrictions.

## Production execution

Web: customer-chat API → validated published channel → pinned session version → leased Agent → persisted result.
IM: channel connection → unified identity → persisted input → automation policy → Gateway/Agent → policy recheck → outbound delivery.

Both entry paths reject missing or invalid releases. Draft execution uses the Agent debug API only.
Existing web conversations retain their original release. New web sessions use the latest promoted release.
Runtime cache eviction/replacement never deletes business history. IM gateways are rebuilt on channel or release changes;
editing an Agent draft does not restart production channels.

New snapshots include model endpoint/name, MCP and skill configurations, pinned subagents and intent classification rules.
Secrets are resolved separately from snapshots. Legacy snapshots without intent rules skip pre-classification.

## Decision model configuration

`DECISION` assets are managed separately from chat (`LLM`) assets. Agent drafts optionally reference
`decisionModelAssetId`; the primary model still requires an enabled LLM. Migration V55 adds the nullable
reference, so existing configurations need no backfill. Saving rejects unavailable, disabled or wrongly
classified model references.

New release snapshots freeze the decision model's asset ID, provider, model ID and endpoint under
`refs.decisionModel`; API keys remain outside the snapshot and are resolved through the managed asset
credential reference. Editing a draft or model endpoint cannot change a published decision configuration.
Legacy snapshots without this field retain their existing classification behavior.

The `decision` runtime module exposes a provider-neutral bounded-choice SDK and a JEV adapter.
The production web intent pre-classifier uses it when a frozen decision model is configured; otherwise
it preserves LLM classification. Configured decision models also run in Harness middleware for IM and debug. Web marks pre-classified turns to avoid duplicate calls. Leaf delegates do not classify again. Debug resolves current enabled decision assets and intent rules; production uses frozen snapshots.
The JEV preset uses `jev-latest` and `https://api.typesafe.ai/v1/systemone` ([official API](https://docs.typesafe.ai/api)).
The adapter also accepts a provider root or `/v1` base and appends the System One path locally.
It reserves one explicit no-match option, allowing up to 254 application intents. It validates returned
choices and the official confidence value; the existing 0.6 routing threshold remains in effect and
should be calibrated against real requests before production use. An exact model version can be configured
instead of a moving alias when threshold stability matters.

Decision calls make one attempt, bounded by the Agent model timeout and a 30-second cap. No-match and
low confidence preserve fallback to the parent Agent. Decision provider/configuration failures surface as
a sanitized gateway error, rather than silently bypassing the configured classifier.
Failures emit sanitized kind/status and latency logs, never provider bodies, prompts or credentials.
There is no hidden secondary classifier or provider retry. Connection tests use the decision protocol.
A provider-neutral decision may omit confidence; the chat API then returns null and the UI omits it.
JEV's own adapter requires the confidence field specified by its API.

## Human handling

Only `OPEN` conversations permit automated replies. Incoming messages remain recorded during human handling.
The policy is checked before generation and again before outbound delivery. An explicit resume operation clears human assignment.
An already transmitted provider request cannot be recalled. Operator reply delivery and provider-specific cancellation semantics
must be validated with each provider before claiming end-to-end human service coverage.

## Storage and concurrency

MySQL schemas are managed by Flyway. The in-memory H2 regression suite is complemented by a disposable local MySQL process migration gate.
Account `security_version` invalidates signed tokens after password/access changes; current account state is checked per request.
Dialogue sequence allocation is transactional; message counts derive from that sequence. Optimistic edit conflicts return 409.

The runtime pool coordinates active leases, bounded capacity, eviction and shutdown inside one process.
Channel lifecycles use independent monitors. Deploy one runtime owner: multi-replica channel consumption and execution still
require deployment-level ownership/leader election. A local pool is not a distributed lock.

## Query and UI boundaries

The inbox pages customers in the database, with matching sessions retained for channel grouping. Associated names use batch queries.
Message details use a bounded exclusive sequence cursor. Very large histories for a single customer may still require a separate
session-page API; the customer page currently returns that customer's matching sessions.

React modules load lazily, use shared pagination and centralized bilingual strings. Node 22 is pinned in `.nvmrc` and engines.
The navigation includes unfinished product destinations; their WIP labels must remain until those capabilities are implemented.

## Validation

See `TESTING.md` and `optimization-log.md` for commands and migration effects.
Unit/integration checks do not prove live provider behavior. Use only synthetic records and disposable databases for validation.
