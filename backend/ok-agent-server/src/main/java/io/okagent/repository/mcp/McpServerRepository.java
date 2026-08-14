package io.okagent.repository.mcp;

import io.okagent.domain.mcp.McpServer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServer, UUID> {
  boolean existsByServerKey(String serverKey);
}
