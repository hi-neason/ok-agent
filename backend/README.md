# ok-agent backend

Control-plane and runtime-plane backend for ok-agent.

- Baseline: Java 17 (AgentScope Java 2 requirement), Spring Boot 4.0.3
- Runtime integration: `io.agentscope:agentscope-harness:2.0.2`
- Persistence target: MySQL; repositories and migrations are added with the control-plane domain modules.

Run locally with `mvn spring-boot:run`.

- Health: `GET /actuator/health`
- Platform: `GET /api/v1/platform`

The local AgentScope checkout is at `../agentscope-java`. Its source revision is `2.0.3-SNAPSHOT`; after locally installing that revision, update `agentscope.version` in `pom.xml`.
