package io.okagent.repository.knowledge;

import io.okagent.domain.knowledge.KnowledgeSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {
    boolean existsBySourceKey(String sourceKey);
    Optional<KnowledgeSource> findBySourceKey(String sourceKey);
}
