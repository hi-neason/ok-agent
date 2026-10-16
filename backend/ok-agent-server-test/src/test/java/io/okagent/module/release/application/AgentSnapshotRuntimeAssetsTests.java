package io.okagent.module.release.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.mcp.domain.McpServer;
import io.okagent.module.mcp.domain.McpTransport;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.model.domain.ModelType;
import io.okagent.module.skill.domain.SkillAsset;
import io.okagent.module.skill.domain.SkillSourceType;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.mcp.infrastructure.persistence.McpServerRepository;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.release.infrastructure.persistence.AgentVersionRepository;
import io.okagent.module.skill.infrastructure.persistence.SkillAssetRepository;
import io.okagent.module.model.application.ApiKeyCipher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentSnapshotRuntimeAssetsTests {

    @Test
    void rejectsMissingDisabledAndWrongTypeDecisionReferences() {
        var models = mock(ModelAssetRepository.class);
        var cipher = new ApiKeyCipher("test-key");
        var snapshots = new AgentSnapshotService(mock(AgentAssetRepository.class), mock(AgentVersionRepository.class),
                models, mock(McpServerRepository.class), mock(SkillAssetRepository.class), cipher,
                new ObjectMapper(), mock(io.okagent.module.intent.application.IntentService.class));
        var agent = new AgentAsset(UUID.randomUUID(), "agent", "Agent", "", "GENERAL");
        UUID id = UUID.randomUUID();
        agent.setDecisionModelAssetId(id);
        assertThatThrownBy(() -> snapshots.buildSnapshot(agent))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var model = new ModelAsset(id, "Decision", ModelType.LLM, "jev", "jev", "https://example.com", cipher.encrypt("key"), true);
        when(models.findById(id)).thenReturn(Optional.of(model));
        assertThatThrownBy(() -> snapshots.buildSnapshot(agent))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        model.update("Decision", ModelType.DECISION, "jev", "jev", "https://example.com", null, false);
        assertThatThrownBy(() -> snapshots.buildSnapshot(agent))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void freezesModelMcpAndSkillRuntimeConfiguration() {
        UUID agentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        UUID mcpId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        AgentAsset agent = new AgentAsset(agentId, "router", "Router", "Routes requests", "SUPPORT");
        agent.updateConfiguration(
                "Prompt",
                "Welcome",
                modelId,
                0.3,
                null,
                null,
                1000,
                "[\"" + mcpId + "\"]",
                "[\"" + skillId + "\"]");
        ApiKeyCipher cipher = new ApiKeyCipher("test-key");
        ModelAsset model = new ModelAsset(
                modelId,
                "Model",
                ModelType.LLM,
                "openai",
                "frozen-model",
                "https://model.example/v1",
                cipher.encrypt("secret"),
                true);
        agent.setDecisionModelAssetId(decisionId);
        ModelAsset decision = new ModelAsset(decisionId, "Decision", ModelType.DECISION, "jev", "jev-1",
                "https://decision.example", cipher.encrypt("decision-secret"), true);
        McpServer mcp = new McpServer(
                mcpId,
                "catalog",
                "Catalog",
                "Catalog tools",
                McpTransport.SSE,
                "https://mcp.example/sse",
                null,
                "[\"--frozen\"]",
                "{\"region\":\"cn\"}",
                cipher.encrypt("{}"),
                19,
                7);
        SkillAsset skill = new SkillAsset(
                skillId,
                "frozen-skill",
                "Frozen Skill",
                "Frozen description",
                "1",
                SkillSourceType.FILE_IMPORT,
                null,
                "SKILL.md",
                "# Frozen skill",
                true);

        AgentAssetRepository agents = mock(AgentAssetRepository.class);
        AgentVersionRepository versions = mock(AgentVersionRepository.class);
        ModelAssetRepository models = mock(ModelAssetRepository.class);
        McpServerRepository mcpServers = mock(McpServerRepository.class);
        SkillAssetRepository skills = mock(SkillAssetRepository.class);
        when(models.findById(modelId)).thenReturn(Optional.of(model));
        when(models.findById(decisionId)).thenReturn(Optional.of(decision));
        when(mcpServers.findById(mcpId)).thenReturn(Optional.of(mcp));
        when(skills.findById(skillId)).thenReturn(Optional.of(skill));
        AgentSnapshotService snapshots =
                new AgentSnapshotService(
                        agents, versions, models, mcpServers, skills, cipher, new ObjectMapper(), mock(io.okagent.module.intent.application.IntentService.class));

        String snapshotJson = snapshots.buildSnapshot(agent).snapshotJson();
        decision.update("Changed", ModelType.DECISION, "changed", "changed", "https://changed.example", null, true);
        agent.setDecisionModelAssetId(null);
        model.update(
                "Changed",
                ModelType.LLM,
                "openai",
                "changed-model",
                "https://changed.example/v1",
                null,
                true);
        mcp.update(
                "changed-catalog",
                "Changed",
                "Changed",
                McpTransport.STDIO,
                null,
                "changed-command",
                "[]",
                "{}",
                null,
                99,
                99);
        skill.update(
                "changed-skill",
                "Changed",
                "Changed",
                "2",
                SkillSourceType.FILE_IMPORT,
                null,
                "SKILL.md",
                "# Changed",
                true);

        ReleaseAgentConfig frozen = ReleaseAgentConfig.fromSnapshot(snapshotJson);

        assertThat(snapshotJson).doesNotContain("decision-secret", decision.getApiKeyCiphertext());
        assertThat(frozen.getDecisionModelAssetId()).isEqualTo(decisionId);
        assertThat(frozen.getResolvedDecisionModelAsset().provider()).isEqualTo("jev");
        assertThat(frozen.getResolvedDecisionModelAsset().modelId()).isEqualTo("jev-1");
        assertThat(frozen.getResolvedDecisionModelAsset().endpoint()).isEqualTo("https://decision.example");
        assertThat(frozen.getResolvedModelAsset().modelId()).isEqualTo("frozen-model");
        assertThat(frozen.getResolvedMcpServers()).singleElement().satisfies(server -> {
            assertThat(server.serverKey()).isEqualTo("catalog");
            assertThat(server.queryParameters()).containsEntry("region", "cn");
            assertThat(server.requestTimeoutSeconds()).isEqualTo(19);
        });
        assertThat(frozen.getResolvedSkillAssets()).singleElement().satisfies(resolved -> {
            assertThat(resolved.skillKey()).isEqualTo("frozen-skill");
            assertThat(resolved.content()).isEqualTo("# Frozen skill");
        });
    }
}
