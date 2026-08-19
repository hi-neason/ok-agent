package io.okagent.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.okagent.domain.agent.AgentAsset;
import io.okagent.domain.agent.AgentMemoryFlushMode;
import io.okagent.domain.agent.AgentPermissionMode;
import io.okagent.domain.agent.AgentWorkspaceMode;
import io.okagent.domain.mcp.McpServer;
import io.okagent.domain.mcp.McpToolSnapshot;
import io.okagent.domain.model.ModelAsset;
import io.okagent.domain.skill.SkillAsset;
import io.okagent.repository.agent.AgentAssetRepository;
import io.okagent.repository.mcp.McpServerRepository;
import io.okagent.repository.mcp.McpToolSnapshotRepository;
import io.okagent.repository.model.ModelAssetRepository;
import io.okagent.repository.skill.SkillAssetRepository;
import io.okagent.web.agent.AgentConfigRequest;
import io.okagent.web.agent.AgentConfigValidationResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentConfigurationValidationTests {

    @Mock
    private AgentAssetRepository agents;

    @Mock
    private ModelAssetRepository models;

    @Mock
    private McpServerRepository mcpServers;

    @Mock
    private SkillAssetRepository skills;

    @Mock
    private McpToolSnapshotRepository mcpToolSnapshots;

    private final UUID agentId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();
    private final UUID mcpId = UUID.randomUUID();
    private final UUID skillId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(agents.findById(any())).thenReturn(Optional.of(mock(AgentAsset.class)));

        var model = mock(ModelAsset.class);
        when(model.isEnabled()).thenReturn(true);
        when(model.getApiKeyCiphertext()).thenReturn("enc");
        when(models.findById(modelId)).thenReturn(Optional.of(model));

        var mcp = mock(McpServer.class);
        when(mcp.isEnabled()).thenReturn(true);
        when(mcp.getName()).thenReturn("Demo");
        when(mcpServers.findById(mcpId)).thenReturn(Optional.of(mcp));

        var skill = mock(SkillAsset.class);
        when(skill.isEnabled()).thenReturn(true);
        when(skill.getName()).thenReturn("Demo Skill");
        when(skills.findById(skillId)).thenReturn(Optional.of(skill));

        var tool = mock(McpToolSnapshot.class);
        when(tool.getName()).thenReturn("clock");
        when(mcpToolSnapshots.findByServerIdOrderByName(mcpId)).thenReturn(List.of(tool));
    }

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private AgentAssetServiceImpl service() {
        return new AgentAssetServiceImpl(
                agents,
                models,
                mcpServers,
                skills,
                mcpToolSnapshots,
                validator,
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private Req validReq() {
        Req r = new Req();
        r.modelId = modelId;
        r.mcpId = mcpId;
        r.skillId = skillId;
        r.mcpServerIds = List.of(mcpId);
        r.skillIds = List.of(skillId);
        r.mcpToolFilters = Map.of(mcpId.toString(), List.of("clock"));
        return r;
    }

    @Test
    void acceptsAWellFormedConfiguration() {
        AgentConfigValidationResponse response =
                service().validateConfiguration(agentId, validReq().build());

        assertThat(response.valid()).isTrue();
        assertThat(response.errors()).isEmpty();
        assertThat(response.checks()).isNotEmpty();
    }

    @Test
    void reportsNotFoundAgentAsError() {
        when(agents.findById(any())).thenReturn(Optional.empty());

        AgentConfigValidationResponse response =
                service().validateConfiguration(agentId, validReq().build());

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> "AGENT_NOT_FOUND".equals(e.code()));
    }

    @Test
    void rejectsDockerSandboxWithoutImage() {
        Req r = validReq();
        r.workspaceMode = AgentWorkspaceMode.DOCKER_SANDBOX;
        r.dockerImage = "";

        AgentConfigValidationResponse response = service().validateConfiguration(agentId, r.build());

        assertThat(response.valid()).isFalse();
        assertThat(response.errors())
                .anyMatch(e -> "DOCKER_IMAGE_REQUIRED".equals(e.code()) && "workspace".equals(e.tab()));
    }

    @Test
    void rejectsToolFilterForUnboundServer() {
        Req r = validReq();
        UUID unbound = UUID.randomUUID();
        r.mcpToolFilters = Map.of(unbound.toString(), List.of("clock"));

        AgentConfigValidationResponse response = service().validateConfiguration(agentId, r.build());

        assertThat(response.errors()).anyMatch(e -> "MCP_FILTER_UNBOUND_SERVER".equals(e.code()));
    }

    @Test
    void warnsWhenAllowlistedToolIsNotDiscovered() {
        Req r = validReq();
        r.mcpToolFilters = Map.of(mcpId.toString(), List.of("nonexistent-tool"));

        AgentConfigValidationResponse response = service().validateConfiguration(agentId, r.build());

        assertThat(response.warnings()).anyMatch(w -> "MCP_TOOL_NOT_DISCOVERED".equals(w.code()));
    }

    @Test
    void rejectsMissingModelAndDisabledSkill() {
        when(models.findById(modelId)).thenReturn(Optional.empty());
        var disabledSkill = mock(SkillAsset.class);
        when(disabledSkill.isEnabled()).thenReturn(false);
        when(disabledSkill.getName()).thenReturn("Demo Skill");
        when(skills.findById(skillId)).thenReturn(Optional.of(disabledSkill));

        AgentConfigValidationResponse response =
                service().validateConfiguration(agentId, validReq().build());

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> "MODEL_NOT_FOUND".equals(e.code()));
        assertThat(response.errors()).anyMatch(e -> "SKILL_DISABLED".equals(e.code()));
    }

    @Test
    void rejectsMaxTokensExceedingContextBudget() {
        Req r = validReq();
        r.maxTokens = 9000;
        r.maxContextTokens = 8000;

        AgentConfigValidationResponse response = service().validateConfiguration(agentId, r.build());

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> "CONTEXT_BUDGET_INVALID".equals(e.code()));
    }

    @Test
    void reportsBeanValidationViolationsAsStructuredErrors() {
        Req r = validReq();
        r.maxContextTokens = 0;
        r.maxTokens = 0;

        AgentConfigValidationResponse response = service().validateConfiguration(agentId, r.build());

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(e -> "maxContextTokens".equals(e.field()) && "runtime".equals(e.tab()));
        assertThat(response.errors()).anyMatch(e -> "maxTokens".equals(e.field()) && "core".equals(e.tab()));
    }

    /** Mutable holder for the 33-field {@link AgentConfigRequest} record. */
    private static final class Req {
        UUID modelId = UUID.randomUUID();
        UUID mcpId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        String systemPrompt = "You are helpful.";
        String welcomeMessage = "Hi";
        Double temperature = 0.7;
        Double topP = 1.0;
        Integer topK = 40;
        int maxTokens = 2048;
        int maxIters = 10;
        int modelTimeoutSeconds = 120;
        int toolTimeoutSeconds = 60;
        int maxRetries = 2;
        AgentPermissionMode permissionMode = AgentPermissionMode.BYPASS;
        boolean parallelToolCalls = true;
        boolean compactionEnabled = true;
        int maxContextTokens = 8000;
        boolean toolResultEvictionEnabled = true;
        boolean tracingEnabled = true;
        List<UUID> mcpServerIds = List.of();
        List<UUID> skillIds = List.of();
        Map<String, List<String>> mcpToolFilters = Map.of();
        boolean memoryEnabled = false;
        AgentMemoryFlushMode memoryFlushMode = AgentMemoryFlushMode.THROTTLED;
        int memoryFlushIntervalMinutes = 30;
        int memoryConsolidationIntervalMinutes = 30;
        int memoryDailyRetentionDays = 90;
        int memorySessionRetentionDays = 180;
        boolean personaExtractEnabled = false;
        io.okagent.domain.agent.PersonaInjectionMode personaInjectionMode =
                io.okagent.domain.agent.PersonaInjectionMode.NONE;
        String personaPromptTemplate = "";
        AgentWorkspaceMode workspaceMode = AgentWorkspaceMode.LOCAL_ROOTED;
        String workspaceIsolationScope = "SESSION";
        boolean workspaceContextEnabled = true;
        boolean shellEnabled = false;
        String dockerImage = "";
        int sandboxMemoryMb = 512;
        int sandboxCpuCount = 1;
        String subagentsJson = "";

        AgentConfigRequest build() {
            return new AgentConfigRequest(
                    systemPrompt,
                    welcomeMessage,
                    modelId,
                    temperature,
                    topP,
                    topK,
                    maxTokens,
                    maxIters,
                    modelTimeoutSeconds,
                    toolTimeoutSeconds,
                    maxRetries,
                    permissionMode,
                    parallelToolCalls,
                    compactionEnabled,
                    maxContextTokens,
                    toolResultEvictionEnabled,
                    tracingEnabled,
                    mcpServerIds,
                    skillIds,
                    mcpToolFilters,
                    memoryEnabled,
                    memoryFlushMode,
                    memoryFlushIntervalMinutes,
                    memoryConsolidationIntervalMinutes,
                    memoryDailyRetentionDays,
                    memorySessionRetentionDays,
                    personaExtractEnabled,
                    personaInjectionMode,
                    personaPromptTemplate,
                    workspaceMode,
                    workspaceIsolationScope,
                    workspaceContextEnabled,
                    shellEnabled,
                    dockerImage,
                    sandboxMemoryMb,
                    sandboxCpuCount,
                    subagentsJson);
        }
    }
}
