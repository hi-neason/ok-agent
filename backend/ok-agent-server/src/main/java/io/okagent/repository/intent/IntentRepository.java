package io.okagent.repository.intent;

import io.okagent.domain.intent.Intent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntentRepository extends JpaRepository<Intent, UUID> {
    List<Intent> findByParentId(UUID parentId);

    List<Intent> findByParentIdIsNullOrderByNameAsc();

    Optional<Intent> findByIntentKey(String intentKey);

    long countByParentId(UUID parentId);

    void deleteByParentId(UUID parentId);
}
