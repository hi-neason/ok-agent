# ok-agent backend

Control-plane and runtime-plane backend for ok-agent.

- Baseline: Java 17 (AgentScope Java 2 requirement), Spring Boot 4.0.3
- Runtime integration: `io.agentscope:agentscope-harness:2.0.2`
- Persistence target: MySQL; repositories and migrations are added with the control-plane domain modules.

Set a private encryption key before starting the service. Keep this value stable: changing it makes
previously stored model, channel, MCP, knowledge, workflow and product credentials unreadable.

```bash
export OK_AGENT_API_KEY_ENCRYPTION_KEY="replace-with-a-long-random-secret"
export OK_AGENT_BOOTSTRAP_ADMIN_PASSWORD="replace-with-at-least-12-characters"
export OK_AGENT_JWT_SECRET="replace-with-at-least-32-random-characters"
mvn -pl ok-agent-server spring-boot:run
```

The bootstrap password initializes `OK_AGENT_BOOTSTRAP_ADMIN_USERNAME` (default `admin`) only when
that account has no credentials; later restarts never overwrite its password. Access tokens expire
after 30 minutes by default (`OK_AGENT_JWT_TTL`). The MVP roles are:

- `ADMIN`: account, channel, release, and all configuration operations.
- `EDITOR`: read access plus ordinary configuration and debug operations.
- `VIEWER`: read-only API access.

Run all tests with `mvn test`. Production code lives in `ok-agent-server/`; all test code and test-only dependencies live in `ok-agent-server-test/`.

Execution traces are retained for 30 days by default. Override this with
`OK_AGENT_TRACE_RETENTION_DAYS` (1–3650); cleanup runs daily at 03:17 UTC.

- Health: `GET /actuator/health`
- Platform: `GET /api/v1/platform`

The local AgentScope checkout is at `../agentscope-java`. Its source revision is `2.0.3-SNAPSHOT`; after locally installing that revision, update `agentscope.version` in `pom.xml`.
