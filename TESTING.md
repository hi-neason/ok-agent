# Regression gates

Run before pushing:

- Backend: `cd backend && mvn test` (Java 17+).
- Frontend: `cd frontend && npm ci && npm test && npm run build` (Node 22).

The GitHub Actions workflow runs both gates on pushes and pull requests and retains
backend test reports. Configure these jobs as required branch checks separately;
adding a workflow alone does not prevent merges.

## Covered failure modes

- Sixteen paginated APIs: maximum page size, invalid values, page envelope shape.
- Real Spring service transactions: user and assistant persistence, sequence
  allocation, failed-write rollback, persisted channel type. These tests must not
  have a test-level transaction, which would hide a missing service transaction.
- Frontend catalog pagination: agent, model, skills, MCP, product, user/group,
  release, workflow, knowledge and persona lists; later pages and failure paths.
- Authentication wrapper: envelope unwrapping, errors, empty responses, streaming,
  unauthorized session expiry and no token injection into external requests.
- Customer aggregation: stable identity, channel grouping, ordering, anonymous
  isolation and equal display names.
- Existing release snapshot and resolver tests remain part of the backend gate.

## Not yet covered

These are not browser end-to-end tests. Real page rendering, interaction, save
and reload flows still need browser automation. H2 tests do not validate Flyway
migrations against MySQL. Real provider delivery (Feishu, DingTalk, WeChat) is
also outside these gates. Passing checks must not be described as proof that
those external integrations or complete user journeys work.

Never point tests at the developer's real database, run real model calls, or
include production credentials or customer messages in fixtures.
