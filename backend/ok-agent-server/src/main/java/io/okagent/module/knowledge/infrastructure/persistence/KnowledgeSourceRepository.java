package io.okagent.module.knowledge.infrastructure.persistence;

import io.okagent.module.knowledge.domain.KnowledgeSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {
    boolean existsBySourceKey(String sourceKey);

    Optional<KnowledgeSource> findBySourceKey(String sourceKey);
}
