package io.okagent.module.knowledge.infrastructure.persistence;

import io.okagent.module.knowledge.domain.KnowledgeCatalogItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeCatalogItemRepository extends JpaRepository<KnowledgeCatalogItem, UUID> {
    List<KnowledgeCatalogItem> findBySourceId(UUID sourceId);

    Optional<KnowledgeCatalogItem> findBySourceIdAndRemoteKnowledgeId(UUID sourceId, String remoteKnowledgeId);

    void deleteBySourceId(UUID sourceId);

    List<KnowledgeCatalogItem> findAllByIdIn(Collection<UUID> ids);
}
