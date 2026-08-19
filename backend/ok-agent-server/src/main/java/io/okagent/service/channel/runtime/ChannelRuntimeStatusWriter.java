package io.okagent.service.channel.runtime;

import io.okagent.domain.channel.ChannelRuntimeStatus;
import io.okagent.repository.channel.ChannelAssetRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a channel's runtime status in a dedicated new transaction via a bulk update query. This
 * bypasses the open-session-in-view persistence context, which can otherwise retain a stale managed
 * entity after the request transaction commits and silently drop a subsequent status write.
 */
@Component
public class ChannelRuntimeStatusWriter {

    private final ChannelAssetRepository repository;

    public ChannelRuntimeStatusWriter(ChannelAssetRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(UUID channelId, ChannelRuntimeStatus status, String error) {
        try {
            repository.updateRuntimeStatus(channelId, status, truncate(error));
        } catch (DataIntegrityViolationException e) {
            // Can race with deletion; ignore.
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
