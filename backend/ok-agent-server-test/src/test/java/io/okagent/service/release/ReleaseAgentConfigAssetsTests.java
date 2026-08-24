package io.okagent.service.release;

import static org.assertj.core.api.Assertions.assertThat;

import io.okagent.domain.mcp.McpTransport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReleaseAgentConfigAssetsTests {

    @Test
    void parsesFrozenRuntimeAssetsFromSnapshot() {
        UUID agentId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        UUID mcpId = UUID.randomUUID();
        String snapshot = """
                {
                  "agentId":"%s","agentKey":"router","name":"Router",
                  "modelAssetId":"%s","mcpServerIds":["%s"],"skillIds":[],"subagents":[],
                  "refs":{
                    "model":{"id":"%s","modelId":"frozen-model","endpoint":"https://frozen.example/v1"},
                    "mcpServers":[{"id":"%s","serverKey":"frozen-mcp","transport":"SSE",
                      "serverUrl":"https://mcp.example/sse","command":null,"arguments":[],
                      "queryParameters":{"region":"cn"},"requestTimeoutSeconds":21,
                      "initializationTimeoutSeconds":8}],
                    "skills":[{"skillKey":"frozen-skill","description":"Frozen","content":"# Frozen"}]
                  }
                }
                """.formatted(agentId, modelId, mcpId, modelId, mcpId);

        ReleaseAgentConfig config = ReleaseAgentConfig.fromSnapshot(snapshot);

        assertThat(config.getResolvedModelAsset().modelId()).isEqualTo("frozen-model");
        assertThat(config.getResolvedModelAsset().endpoint()).isEqualTo("https://frozen.example/v1");
        assertThat(config.getResolvedMcpServers()).singleElement().satisfies(server -> {
            assertThat(server.transport()).isEqualTo(McpTransport.SSE);
            assertThat(server.serverUrl()).isEqualTo("https://mcp.example/sse");
            assertThat(server.queryParameters()).containsEntry("region", "cn");
            assertThat(server.requestTimeoutSeconds()).isEqualTo(21);
        });
        assertThat(config.getResolvedSkillAssets()).singleElement().satisfies(skill -> {
            assertThat(skill.skillKey()).isEqualTo("frozen-skill");
            assertThat(skill.content()).isEqualTo("# Frozen");
        });
    }

    @Test
    void ignoresLegacyMcpAndSkillFingerprintsWithoutFrozenConfiguration() {
        UUID agentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        String snapshot = """
                {
                  "agentId":"%s","agentKey":"router","name":"Router","subagents":[],
                  "refs":{
                    "mcpServers":[{"id":"%s","name":"Legacy","updatedAt":"2026-01-01T00:00:00Z"}],
                    "skills":[{"id":"%s","name":"Legacy","archiveSha256":"abc"}]
                  }
                }
                """.formatted(agentId, assetId, assetId);

        ReleaseAgentConfig config = ReleaseAgentConfig.fromSnapshot(snapshot);

        assertThat(config.getResolvedMcpServers()).isEmpty();
        assertThat(config.getResolvedSkillAssets()).isEmpty();
    }
}
