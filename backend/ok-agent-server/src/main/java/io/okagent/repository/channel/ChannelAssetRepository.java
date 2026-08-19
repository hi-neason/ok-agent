package io.okagent.repository.channel;

import io.okagent.domain.channel.ChannelAsset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelAssetRepository extends JpaRepository<ChannelAsset, UUID> {
    List<ChannelAsset> findByBoundAgentId(UUID boundAgentId);

    List<ChannelAsset> findByEnabledTrue();
}
