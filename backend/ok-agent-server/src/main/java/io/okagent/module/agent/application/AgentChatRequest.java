package io.okagent.module.agent.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
        @NotBlank(message = "消息内容不能为空") @Size(max = 20000, message = "消息内容不能超过 20000 字") String message,
        @Size(max = 64) String sessionId,
        @NotBlank(message = "请选择调试用户") @Size(max = 128) String userId) {}
