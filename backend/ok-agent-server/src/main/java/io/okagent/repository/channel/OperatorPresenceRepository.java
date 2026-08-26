package io.okagent.repository.channel;

import io.okagent.domain.channel.OperatorPresence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorPresenceRepository extends JpaRepository<OperatorPresence, UUID> {}
