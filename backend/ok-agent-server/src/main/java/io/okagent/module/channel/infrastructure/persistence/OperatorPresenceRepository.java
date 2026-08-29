package io.okagent.module.channel.infrastructure.persistence;

import io.okagent.module.channel.domain.OperatorPresence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorPresenceRepository extends JpaRepository<OperatorPresence, UUID> {}
