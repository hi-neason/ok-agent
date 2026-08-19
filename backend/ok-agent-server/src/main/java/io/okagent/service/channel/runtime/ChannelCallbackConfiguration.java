package io.okagent.service.channel.runtime;

import io.agentscope.extensions.channel.feishu.FeishuCallbackController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the AgentScope 2.0.2 Feishu callback controller with the Spring MVC context. The
 * controller lives in the {@code io.agentscope.extensions.channel.feishu} package (outside the
 * {@code io.okagent} component scan) and is otherwise self-contained: it receives callbacks at
 * {@code POST /api/channels/feishu/{channelId}/callback}, looks up the live FeishuChannel via the
 * static FeishuChannelRegistry, and dispatches into the framework Gateway.
 */
@Configuration
@Import(FeishuCallbackController.class)
public class ChannelCallbackConfiguration {}
