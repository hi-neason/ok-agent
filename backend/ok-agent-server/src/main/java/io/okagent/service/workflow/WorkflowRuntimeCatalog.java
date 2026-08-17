package io.okagent.service.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.domain.workflow.AgentWorkflowBinding;
import io.okagent.domain.workflow.WorkflowCatalogItem;
import io.okagent.domain.workflow.WorkflowExecutionAudit;
import io.okagent.domain.workflow.WorkflowSource;
import io.okagent.repository.workflow.AgentWorkflowBindingRepository;
import io.okagent.repository.workflow.WorkflowCatalogItemRepository;
import io.okagent.repository.workflow.WorkflowExecutionAuditRepository;
import io.okagent.repository.workflow.WorkflowSourceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runtime-facing catalog used by the agent workflow tools. Resolves an agent's bindings, authorizes
 * workflow access, delegates execution to the matching {@link WorkflowProvider}, and writes audit
 * records. This is the only runtime path that triggers external workflows.
 */
@Service
public class WorkflowRuntimeCatalog {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeCatalog.class);
    private static final Duration IDEMPOTENCY_WINDOW = Duration.ofMinutes(5);

    private final AgentWorkflowBindingRepository bindings;
    private final WorkflowCatalogItemRepository items;
    private final WorkflowSourceRepository sources;
    private final WorkflowExecutionAuditRepository audits;
    private final WorkflowSourceServiceImpl sourceService;
    private final List<WorkflowProvider> providers;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Long> recentExecutions = new ConcurrentHashMap<>();

    public WorkflowRuntimeCatalog(
            AgentWorkflowBindingRepository bindings,
            WorkflowCatalogItemRepository items,
            WorkflowSourceRepository sources,
            WorkflowExecutionAuditRepository audits,
            WorkflowSourceServiceImpl sourceService,
            List<WorkflowProvider> providers) {
        this.bindings = bindings;
        this.items = items;
        this.sources = sources;
        this.audits = audits;
        this.sourceService = sourceService;
        this.providers = providers;
    }

    /** Returns the enabled workflows bound to the given agent, with overrides applied. */
    public List<BoundWorkflow> listForAgent(UUID agentId) {
        var byItemId = new HashMap<UUID, WorkflowCatalogItem>();
        for (var item : items.findAllByIdIn(bindingItemIds(agentId))) {
            byItemId.put(item.getId(), item);
        }
        var sourceNames = sourceNames(byItemId.values());
        List<BoundWorkflow> result = new ArrayList<>();
        for (var binding : bindings.findByAgentId(agentId)) {
            if (!binding.isEnabled()) continue;
            var item = byItemId.get(binding.getCatalogItemId());
            if (item == null || !item.isActive()) continue;
            String description = binding.getDescriptionOverride();
            if (description == null || description.isBlank()) {
                description = item.getDescription().isBlank() ? item.getRemoteDescription() : item.getDescription();
            }
            result.add(new BoundWorkflow(
                    item.getId(),
                    item.getSourceId(),
                    sourceNames.getOrDefault(item.getSourceId(), ""),
                    item.getRemoteWorkflowId(),
                    item.getName(),
                    description == null ? "" : description,
                    item.isActive(),
                    binding.getParameterDefaultsJson()));
        }
        return result;
    }

    /** Returns the input schema (JSON Schema text) and parameter defaults for a bound workflow. */
    public DescribeResult describe(UUID agentId, UUID catalogItemId) {
        var item = requireBoundItem(agentId, catalogItemId);
        var binding = bindings.findByAgentId(agentId).stream()
                .filter(b -> b.getCatalogItemId().equals(catalogItemId))
                .findFirst()
                .orElse(null);
        return new DescribeResult(
                item.getName(),
                item.getInputSchemaJson() == null || item.getInputSchemaJson().isBlank()
                        ? "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
                        : item.getInputSchemaJson(),
                binding == null ? null : binding.getParameterDefaultsJson());
    }

    /** Authorizes, dedupes, executes a bound workflow, and records an audit entry. */
    public ExecuteResult execute(
            UUID agentId,
            UUID catalogItemId,
            Map<String, Object> inputs,
            String userId,
            String sessionId) {
        long started = System.nanoTime();
        var item = requireBoundItem(agentId, catalogItemId);
        var source = sources.findById(item.getSourceId())
                .orElseThrow(() -> new IllegalStateException("Workflow source not found: " + item.getSourceId()));
        if (!source.isEnabled()) {
            throw new IllegalStateException("Workflow source '" + source.getName() + "' is disabled");
        }
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        String inputsHash = hashInputs(safeInputs);
        String idempotencyKey = sessionId + "|" + catalogItemId + "|" + inputsHash;
        if (isDuplicate(idempotencyKey)) {
            log.info("Suppressing duplicate workflow execution: agent={} item={} session={}",
                    agentId, catalogItemId, sessionId);
            return new ExecuteResult(false, "DUPLICATE", "Duplicate request suppressed; workflow already triggered",
                    null, null, null);
        }
        recentExecutions.put(idempotencyKey, System.currentTimeMillis());

        var provider = providerFor(source);
        WorkflowSourceConfig config = sourceService.toConfig(source);
        WorkflowExecutionResult result;
        try {
            result = provider.execute(config, item.getRemoteWorkflowId(), safeInputs, userId);
        } catch (Exception e) {
            result = WorkflowExecutionResult.failure(null, e.getMessage());
        }
        int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
        audits.save(new WorkflowExecutionAudit(
                UUID.randomUUID(),
                agentId,
                userId,
                sessionId,
                source.getId(),
                catalogItemId,
                inputsHash,
                result.remoteRunId(),
                result.status(),
                result.outputSummary(),
                result.errorMessage(),
                result.elapsedSeconds(),
                result.totalTokens(),
                latencyMs));
        return new ExecuteResult(
                result.success(),
                result.status(),
                result.success() ? result.outputSummary() : result.errorMessage(),
                result.remoteRunId(),
                result.elapsedSeconds(),
                result.totalTokens());
    }

    public record DescribeResult(String name, String inputSchemaJson, String parameterDefaultsJson) {}

    public record ExecuteResult(
            boolean success,
            String status,
            String message,
            String remoteRunId,
            Double elapsedSeconds,
            Integer totalTokens) {}

    private WorkflowCatalogItem requireBoundItem(UUID agentId, UUID catalogItemId) {
        boolean bound = bindings.findByAgentId(agentId).stream()
                .anyMatch(b -> b.isEnabled() && b.getCatalogItemId().equals(catalogItemId));
        if (!bound) {
            throw new SecurityException("Workflow is not bound to this agent: " + catalogItemId);
        }
        return items.findById(catalogItemId)
                .orElseThrow(() -> new IllegalStateException("Workflow catalog item not found: " + catalogItemId));
    }

    private List<UUID> bindingItemIds(UUID agentId) {
        return bindings.findByAgentId(agentId).stream()
                .filter(AgentWorkflowBinding::isEnabled)
                .map(AgentWorkflowBinding::getCatalogItemId)
                .toList();
    }

    private Map<UUID, String> sourceNames(Collection<WorkflowCatalogItem> items) {
        var ids = items.stream().map(WorkflowCatalogItem::getSourceId).distinct().toList();
        Map<UUID, String> names = new HashMap<>();
        for (var source : sources.findAllById(ids)) {
            names.put(source.getId(), source.getSourceKey());
        }
        return names;
    }

    private WorkflowProvider providerFor(WorkflowSource source) {
        return providers.stream()
                .filter(p -> p.type().equalsIgnoreCase(source.getSourceType().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No workflow provider for type " + source.getSourceType()));
    }

    private boolean isDuplicate(String key) {
        long now = System.currentTimeMillis();
        recentExecutions.entrySet().removeIf(e -> now - e.getValue() > IDEMPOTENCY_WINDOW.toMillis());
        return recentExecutions.containsKey(key);
    }

    private String hashInputs(Map<String, Object> inputs) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(inputs).getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(inputs.hashCode());
        }
    }
}
