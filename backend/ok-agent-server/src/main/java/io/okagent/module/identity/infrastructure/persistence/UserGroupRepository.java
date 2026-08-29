package io.okagent.module.identity.infrastructure.persistence;

import io.okagent.module.identity.domain.UserGroup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {
    Optional<UserGroup> findByGroupKey(String groupKey);

    boolean existsByGroupKey(String groupKey);

    boolean existsByGroupKeyAndIdNot(String groupKey, UUID id);
}
