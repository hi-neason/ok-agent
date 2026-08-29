package io.okagent.module.mcp.infrastructure.persistence;

import io.okagent.module.mcp.domain.McpToolSnapshot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface McpToolSnapshotRepository extends JpaRepository<McpToolSnapshot, UUID> {
    List<McpToolSnapshot> findByServerIdOrderByName(UUID serverId);

    @Transactional
    void deleteByServerId(UUID serverId);
}
