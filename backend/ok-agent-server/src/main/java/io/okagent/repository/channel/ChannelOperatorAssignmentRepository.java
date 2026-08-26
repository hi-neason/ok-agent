package io.okagent.repository.channel;

import io.okagent.domain.channel.ChannelOperatorAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelOperatorAssignmentRepository extends JpaRepository<ChannelOperatorAssignment, UUID> {
    List<ChannelOperatorAssignment> findByChannelIdOrderByCreatedAtAsc(UUID channelId);
    List<ChannelOperatorAssignment> findByOperatorAccountIdOrderByCreatedAtAsc(UUID operatorAccountId);
    void deleteByChannelId(UUID channelId);
}
