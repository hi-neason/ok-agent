package io.okagent.web.agent;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AgentConfigRequest(
        @Size(max = 200000) String systemPrompt,
        @Size(max = 2048) String welcomeMessage,
        UUID modelAssetId,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
        @Min(1) Integer topK,
        @Min(1) @Max(1_000_000) Integer maxTokens,
        List<UUID> mcpServerIds,
        List<UUID> skillIds) {}
