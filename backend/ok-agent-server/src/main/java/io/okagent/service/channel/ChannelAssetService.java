package io.okagent.service.channel;

import io.okagent.web.channel.ChannelAssetRequest;
import io.okagent.web.channel.ChannelAssetResponse;
import java.util.List;
import java.util.UUID;

/**
 * Lifecycle and CRUD operations for channel assets. Secrets are encrypted at rest and never
 * returned in responses; starting/stopping a channel drives the framework runtime.
 */
public interface ChannelAssetService {

    /** Returns all channel assets in the management scope. */
    List<ChannelAssetResponse> list();

    /** Creates a new channel instance with an unguessable channel key. */
    ChannelAssetResponse create(ChannelAssetRequest request);

    /** Replaces the editable configuration of an existing channel. */
    ChannelAssetResponse update(UUID id, ChannelAssetRequest request);

    /** Enables or disables a channel for runtime activation. */
    ChannelAssetResponse setEnabled(UUID id, boolean enabled);

    /** Starts the runtime channel for the given id. */
    ChannelAssetResponse start(UUID id);

    /** Stops the runtime channel for the given id. */
    ChannelAssetResponse stop(UUID id);

    /** Deletes a channel, stopping its runtime first. */
    void delete(UUID id);
}
