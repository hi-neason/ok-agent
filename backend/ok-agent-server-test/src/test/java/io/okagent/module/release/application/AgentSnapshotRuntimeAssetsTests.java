package io.okagent.module.release.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void freezesModelMcpAndSkillRuntimeConfiguration() {
        UUID agentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
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
        when(mcpServers.findById(mcpId)).thenReturn(Optional.of(mcp));
        when(skills.findById(skillId)).thenReturn(Optional.of(skill));
        AgentSnapshotService snapshots =
                new AgentSnapshotService(agents, versions, models, mcpServers, skills, cipher);

        String snapshotJson = snapshots.buildSnapshot(agent).snapshotJson();
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
