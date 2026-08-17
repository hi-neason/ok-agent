package io.okagent.repository.user;

import io.okagent.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    long countByGroupId(UUID groupId);

    List<User> findByGroupId(UUID groupId);
}
