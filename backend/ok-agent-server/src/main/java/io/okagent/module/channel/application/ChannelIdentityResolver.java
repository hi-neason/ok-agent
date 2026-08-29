package io.okagent.module.channel.application;

import io.okagent.module.channel.domain.ChannelUserIdentity;
import io.okagent.module.identity.domain.User;
import io.okagent.module.channel.infrastructure.persistence.ChannelUserIdentityRepository;
import io.okagent.module.identity.infrastructure.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolves the one-user-id principal for an inbound channel sender.
 *
 * <p>Every provider sender (a Feishu open_id, etc.) maps to one {@code channel_user_identity}
 * row, which in turn links to an {@code app_user} that serves as the unified real-person id
 * ("one-user-id"). On first encounter a {@code source=CHANNEL} app_user is auto-provisioned and
 * linked, so persona/memory/dialogue are keyed by one stable id across that person's sessions.
 *
 * <p>Cross-provider aggregation (same person on Feishu + DingTalk) is done later via the merge
 * API, not here.
 */
@Service
public class ChannelIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(ChannelIdentityResolver.class);

    private final ChannelUserIdentityRepository identities;
    private final UserRepository users;
    private final TransactionTemplate tx;

    public ChannelIdentityResolver(
            ChannelUserIdentityRepository identities, UserRepository users, TransactionTemplate tx) {
        this.identities = identities;
        this.users = users;
        this.tx = tx;
    }

    /**
     * Records the inbound touch and returns the one-user-id ({@code app_user.user_id}) the sender
     * aggregates under. Never throws: a tracking/provisioning failure falls back to the raw
     * external id so message delivery is never blocked.
     */
    public String resolve(
            String channelType,
            String channelKey,
            String externalId,
            String unionId,
            String tenantKey,
            String displayName,
            String avatarUrl) {
        if (externalId == null || externalId.isBlank()) {
            return externalId;
        }
        try {
            // Upsert synchronously so the identity row exists before we look it up (the
            // ChannelUserService async path would race with the find below on first contact).
            identities.upsertTouch(channelType, channelKey, externalId, unionId, tenantKey, displayName, avatarUrl);

            ChannelUserIdentity identity =
                    identities.find(channelType, channelKey, externalId).orElse(null);
            if (identity == null) {
                return externalId;
            }
            // Provision + link in one transaction (TransactionTemplate avoids self-invocation
            // proxy issues and keeps the two writes atomic under concurrent first messages).
            String oneUserId = tx.execute(status -> resolveOneUserId(identity));
            return oneUserId != null ? oneUserId : externalId;
        } catch (Exception e) {
            log.warn(
                    "Channel identity resolution failed for {}:{} on {}, falling back to external id: {}",
                    channelType,
                    externalId,
                    channelKey,
                    e.getMessage());
            return externalId;
        }
    }

    private String resolveOneUserId(ChannelUserIdentity identity) {
        UUID linkedId = identity.getLinkedUserId();
        if (linkedId != null) {
            return users.findById(linkedId).map(User::getUserId).orElse(null);
        }
        return provisionAndLink(identity);
    }

    private String provisionAndLink(ChannelUserIdentity identity) {
        String username = channelUsername(identity);
        // Idempotency under concurrent first messages: another thread/instance may have created
        // the same row. On a username clash, re-query the linked user instead of failing.
        User user = users.findByUsername(username).orElseGet(() -> {
            User created = User.forChannel(
                    UUID.randomUUID(),
                    UUID.randomUUID().toString(),
                    username,
                    identity.getDisplayName(),
                    identity.getAvatarUrl());
            try {
                return users.save(created);
            } catch (DataIntegrityViolationException dup) {
                return users.findByUsername(username).orElseThrow(() -> dup);
            }
        });
        identities.linkUser(identity.getId(), user.getId());
        log.info(
                "Provisioned one-user-id '{}' for channel identity {}:{}",
                user.getUserId(),
                identity.getChannelType(),
                identity.getExternalId());
        return user.getUserId();
    }

    /**
     * Stable, unique placeholder username for a channel-provisioned user: {@code ch:<hex>} where
     * hex is derived from the identity surrogate id.
     */
    private static String channelUsername(ChannelUserIdentity identity) {
        // Uppercase hex to match the V36 backfill (MySQL HEX() is uppercase), so usernames stay
        // consistent between migrated identities and newly provisioned ones.
        UUID id = identity.getId();
        String hex = id != null
                ? HexFormat.of().formatHex(asBytes(id))
                : HexFormat.of().formatHex(identity.getChannelType().getBytes(StandardCharsets.UTF_8));
        return "ch:" + hex.toUpperCase();
    }

    private static byte[] asBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }
}
