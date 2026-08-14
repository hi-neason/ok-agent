# ok-agent AI Coding Guide

## Architecture boundaries

- Keep `frontend/` (React, TypeScript, Vite) and `backend/` (Spring Boot) independent. Do not mix source, build output, or dependency descriptors between them.
- The management plane owns reusable assets, Agent drafts, versions, releases, authorization, and observability. The runtime plane consumes immutable `ReleaseSnapshot` records only; it must never read or mutate drafts.
- `HarnessAgent` is the runtime core. Every management-plane setting must map to an explicit Harness configuration or an auditable platform runtime policy. Do not hide runtime behavior in opaque business code.
- Models, Skills, MCP servers, knowledge bases, and workflows are globally reusable, independently versioned assets. Agents hold references plus agent-local settings only, such as prompts, model parameters, and runtime policies.

## Backend standards

- Use Java 17 as the baseline and Spring Boot 4.x. Do not introduce pre-Java-17 compatibility patterns or unnecessary experimental APIs.
- Organize packages by domain, for example `module/asset`, `module/agent`, `module/release`, `module/observe`, and `module/identity`. Put shared technical capabilities in `shared/`; do not create catch-all `util` packages.
- Prefix APIs with `/api/v1`. Keep request/response DTOs separate from domain objects and validate all inputs with Bean Validation.
- Every Controller endpoint method must have an English Javadoc comment that states its API responsibility and intended effect.
- Every Service must be defined by an interface and implemented by a separate implementation class. Every public method declared in a Service interface must include an English Javadoc comment describing its responsibility.
- Any operation that changes configuration, release state, permissions, or secret references must enforce tenant/project isolation, authorization, auditable events, and explicit state-transition validation.
- Store secrets as `SecretRef` only. Never expose them through entities, API responses, logs, exceptions, fixtures, or Git.
- Manage MySQL schema changes through migrations. Do not rely on ORM schema generation for production changes. Include tenancy, creation/update audit fields, and an optimistic-concurrency strategy where applicable.
- Set timeouts, error classification, and observable context for model, MCP, knowledge-base, and filesystem calls. Never swallow exceptions.

## Frontend and i18n standards

- Use strict TypeScript and do not introduce `any`. Define DTO types and loading, empty, and error states for remote data.
- Follow the four top-level domains: Component Management, Agent Management, Release & Observability, and System Management. Do not merge global asset editing with agent-local configuration data.
- Use the central i18n catalog for every user-visible string. Add equivalent `zh-CN` and `en-US` entries when introducing a new UI string; do not hard-code new UI copy in components.
- Confirm destructive, overriding, release, and permission-changing operations with their target and impact scope.
- Never store model API keys, MCP credentials, or other secrets in the frontend. Render masked references only.

## Quality gates

- Read the affected modules, conventions, and tests before editing. Keep changes focused and preserve unrelated user changes.
- Add or update unit/integration tests for changed backend behavior. Defect fixes should include regression coverage where practical.
- Minimum checks: `cd backend && mvn test` for backend changes; `npm run build` for frontend changes.
- Test both successful and rejected paths for APIs, release snapshots, authorization decisions, and version resolution.
- Use structured, searchable logging context (tenant, project, agent, release, run/trace) without recording secrets or full user prompts.

## Git and delivery

- One commit should address one clear objective. Use Conventional Commit messages, such as `feat: ...`, `fix: ...`, `test: ...`, or `docs: ...`.
- Run the relevant quality gates before committing, and report the actual commands and results in the handoff.
- Do not commit `target/`, `dist/`, local environment files, credentials, logs, or IDE artifacts.
- When a change affects data models, runtime configuration, or release semantics, update the design documentation or state the migration impact in the commit description.
