package io.okagent.module.mcp.infrastructure.persistence;

import io.okagent.module.mcp.domain.McpServer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServer, UUID> {
    boolean existsByServerKey(String serverKey);
}
