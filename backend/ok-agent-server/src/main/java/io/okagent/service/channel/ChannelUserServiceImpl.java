package io.okagent.service.channel;

import io.okagent.domain.channel.ChannelUserIdentity;
import io.okagent.repository.channel.ChannelUserIdentityRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default {@link ChannelUserService} that persists identities via JDBC. Recording happens on a
 * daemon thread and is best-effort: a tracking failure must never interfere with message delivery.
 */
@Service
public class ChannelUserServiceImpl implements ChannelUserService {

    private static final Logger log = LoggerFactory.getLogger(ChannelUserServiceImpl.class);

    private final ChannelUserIdentityRepository repository;

    private final ExecutorService executor = Executors.newFixedThreadPool(1, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "channel-user-track-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    public ChannelUserServiceImpl(ChannelUserIdentityRepository repository) {
        this.repository = repository;
    }

    @Override
    public void recordInbound(
            String channelType,
            String channelKey,
            String externalId,
            String unionId,
            String tenantKey,
            String displayName,
            String avatarUrl) {
        if (channelType == null
                || channelType.isBlank()
                || channelKey == null
                || channelKey.isBlank()
                || externalId == null
                || externalId.isBlank()) {
            return;
        }
        executor.execute(() -> {
            try {
                repository.upsertTouch(channelType, channelKey, externalId, unionId, tenantKey, displayName, avatarUrl);
            } catch (Exception e) {
                log.warn(
                        "Failed to record channel user {}:{} on {}: {}",
                        channelType,
                        externalId,
                        channelKey,
                        e.getMessage());
            }
        });
    }

    @Override
    public List<ChannelUserIdentity> list(String channelType, String channelKey, int limit) {
        return repository.list(blankToNull(channelType), blankToNull(channelKey), limit);
    }

    @Override
    public long countByChannel(String channelKey) {
        return repository.countByChannel(channelKey);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
