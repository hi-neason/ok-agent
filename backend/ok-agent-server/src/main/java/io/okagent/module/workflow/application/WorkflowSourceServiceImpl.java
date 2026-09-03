package io.okagent.module.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.workflow.domain.WorkflowCatalogItem;
import io.okagent.module.workflow.domain.WorkflowMetadataStatus;
import io.okagent.module.workflow.domain.WorkflowSource;
import io.okagent.module.workflow.domain.WorkflowSourceType;
import io.okagent.module.workflow.infrastructure.persistence.WorkflowCatalogItemRepository;
import io.okagent.module.workflow.infrastructure.persistence.WorkflowSourceRepository;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.workflow.application.WorkflowCatalogItemResponse;
import io.okagent.module.workflow.application.WorkflowSourceRequest;
import io.okagent.module.workflow.application.WorkflowSourceResponse;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowSourceServiceImpl implements WorkflowSourceService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowSourceServiceImpl.class);
    private static final int DEFAULT_EXECUTE_TIMEOUT = 90;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    private final WorkflowSourceRepository sources;
    private final WorkflowCatalogItemRepository items;
    private final ApiKeyCipher cipher;
    private final List<WorkflowProvider> providers;
    private final ObjectMapper json;

    public WorkflowSourceServiceImpl(
            WorkflowSourceRepository sources,
            WorkflowCatalogItemRepository items,
            ApiKeyCipher cipher,
            List<WorkflowProvider> providers,
            ObjectMapper json) {
        this.sources = sources;
        this.items = items;
        this.cipher = cipher;
        this.providers = providers;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkflowSourceResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return sources.findAll(pageable).map(this::response);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowSourceResponse get(UUID id) {
        return response(find(id));
    }

    @Override
    @Transactional
    public WorkflowSourceResponse create(WorkflowSourceRequest r) {
        if (sources.existsBySourceKey(r.sourceKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow source key already exists");
        }
        if (r.apiKey() == null || r.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key is required");
        }
        var source = new WorkflowSource(
                UUID.randomUUID(),
                r.sourceKey().trim(),
                r.name().trim(),
                r.sourceType(),
                r.baseUrl().trim(),
                writeConfig(r),
                cipher.encrypt(r.apiKey().trim()));
        return response(sources.save(source));
    }

    @Override
    @Transactional
    public WorkflowSourceResponse update(UUID id, WorkflowSourceRequest r) {
        var source = find(id);
        source.update(
                r.sourceKey().trim(),
                r.name().trim(),
                r.sourceType(),
                r.baseUrl().trim(),
                writeConfig(r),
                (r.apiKey() == null || r.apiKey().isBlank())
                        ? null
                        : cipher.encrypt(r.apiKey().trim()));
        return response(sources.save(source));
    }

    @Override
    @Transactional
    public WorkflowSourceResponse setEnabled(UUID id, boolean enabled) {
        var source = find(id);
        source.setEnabled(enabled);
        return response(sources.save(source));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        sources.deleteById(id);
    }

    @Override
    @Transactional
    public WorkflowSourceResponse test(UUID id) {
        var source = find(id);
        var result = provider(source).test(toConfig(source));
        source.recordTest(result.success() && result.supported(), result.message(), source.getWorkflowCount());
        sources.save(source);
        return response(source);
    }

    @Override
    public WorkflowSourceResponse testDraft(WorkflowSourceRequest r) {
        var source = new WorkflowSource(
                UUID.randomUUID(),
                r.sourceKey(),
                r.name(),
                r.sourceType(),
                r.baseUrl(),
                writeConfig(r),
                r.apiKey() == null ? null : cipher.encrypt(r.apiKey()));
        var result = provider(source).test(toConfig(source));
        return response(source, result.success() && result.supported(), result.message());
    }

    @Override
    @Transactional
    public List<WorkflowCatalogItemResponse> sync(UUID id) {
        var source = find(id);
        var provider = provider(source);
        var config = toConfig(source);

        List<RemoteWorkflowSummary> remote;
        try {
            remote = provider.listWorkflows(config);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list workflows: " + safe(e));
        }

        var existing = new LinkedHashMap<String, WorkflowCatalogItem>();
        for (var item : items.findBySourceId(id)) {
            existing.put(item.getRemoteWorkflowId(), item);
        }

        List<WorkflowCatalogItem> saved = new ArrayList<>();
        for (var summary : remote) {
            RemoteWorkflowDetail detail;
            try {
                detail = provider.describeRemote(config, summary.remoteWorkflowId());
            } catch (Exception e) {
                log.warn("Failed to describe remote workflow {}: {}", summary.remoteWorkflowId(), e.getMessage(), e);
                continue;
            }
            var previous = existing.get(detail.remoteWorkflowId());
            if (previous == null) {
                var item = new WorkflowCatalogItem(
                        UUID.randomUUID(),
                        id,
                        detail.remoteWorkflowId(),
                        detail.name(),
                        detail.remoteMode(),
                        detail.active(),
                        write(detail.tags() == null ? List.of() : detail.tags()),
                        detail.remoteDescription() == null ? "" : detail.remoteDescription(),
                        "",
                        detail.inputSchemaJson(),
                        detail.rawJson() == null ? "{}" : detail.rawJson(),
                        WorkflowMetadataStatus.NEEDS_REVIEW);
                saved.add(items.save(item));
            } else {
                previous.applyRemoteUpdate(
                        detail.name(),
                        detail.remoteMode(),
                        detail.active(),
                        write(detail.tags() == null ? List.of() : detail.tags()),
                        detail.remoteDescription() == null ? "" : detail.remoteDescription(),
                        detail.inputSchemaJson(),
                        detail.rawJson() == null ? "{}" : detail.rawJson());
                saved.add(items.save(previous));
            }
        }
        source.recordSynced(saved.size());
        sources.save(source);
        return saved.stream().map(i -> itemResponse(i, source.getName())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowCatalogItemResponse> catalogItems(UUID sourceId) {
        var sourceName = sources.findById(sourceId).map(WorkflowSource::getName).orElse("");
        return items.findBySourceId(sourceId).stream()
                .sorted(Comparator.comparing(WorkflowCatalogItem::getName))
                .map(i -> itemResponse(i, sourceName))
                .toList();
    }

    @Override
    @Transactional
    public WorkflowCatalogItemResponse updateCatalogDescription(UUID itemId, String description) {
        var item = items.findById(itemId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow catalog item not found"));
        var text = description == null ? "" : description.trim();
        item.updateMetadata(text, text.isBlank() ? WorkflowMetadataStatus.NEEDS_REVIEW : WorkflowMetadataStatus.READY);
        var source = sources.findById(item.getSourceId())
                .map(WorkflowSource::getName)
                .orElse("");
        return itemResponse(items.save(item), source);
    }

    /** Builds a decrypted runtime config for a persisted source; package-access for the runtime catalog. */
    WorkflowSourceConfig toConfig(WorkflowSource source) {
        Map<String, Object> config = readMap(source.getConfigJson());
        Map<String, Object> secrets = new LinkedHashMap<>();
        if (source.getSecretsCiphertext() != null
                && !source.getSecretsCiphertext().isBlank()) {
            secrets.put("apiKey", cipher.decrypt(source.getSecretsCiphertext()));
        }
        int executeTimeout =
                source.getSourceType() == WorkflowSourceType.DIFY ? DEFAULT_EXECUTE_TIMEOUT : DEFAULT_EXECUTE_TIMEOUT;
        int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        Object et = config.get("executeTimeoutSeconds");
        if (et instanceof Number n) executeTimeout = n.intValue();
        Object ct = config.get("connectTimeoutSeconds");
        if (ct instanceof Number n) connectTimeout = n.intValue();
        return new WorkflowSourceConfig(
                source.getId().toString(),
                source.getSourceType().name(),
                source.getBaseUrl(),
                config,
                secrets,
                executeTimeout,
                connectTimeout);
    }

    private WorkflowProvider provider(WorkflowSource source) {
        return providers.stream()
                .filter(p -> p.type().equalsIgnoreCase(source.getSourceType().name()))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("No workflow provider for type " + source.getSourceType()));
    }

    private WorkflowSource find(UUID id) {
        return sources.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow source not found"));
    }

    private WorkflowSourceResponse response(WorkflowSource s) {
        return response(s, "SUCCESS".equals(s.getLastTestStatus()), s.getLastTestMessage());
    }

    private WorkflowSourceResponse response(WorkflowSource s, boolean testPassed, String message) {
        var config = readMap(s.getConfigJson());
        int execute = config.get("executeTimeoutSeconds") instanceof Number n ? n.intValue() : DEFAULT_EXECUTE_TIMEOUT;
        int connect = config.get("connectTimeoutSeconds") instanceof Number n ? n.intValue() : DEFAULT_CONNECT_TIMEOUT;
        return new WorkflowSourceResponse(
                s.getId(),
                s.getSourceKey(),
                s.getName(),
                s.getSourceType(),
                s.getBaseUrl(),
                s.isEnabled(),
                s.getSecretsCiphertext() != null && !s.getSecretsCiphertext().isBlank(),
                execute,
                connect,
                testPassed ? "SUCCESS" : (s.getLastTestStatus() == null ? "UNTESTED" : s.getLastTestStatus()),
                message == null ? "" : message,
                s.getLastTestedAt(),
                s.getLastSyncedAt(),
                s.getWorkflowCount(),
                s.getUpdatedAt());
    }

    private WorkflowCatalogItemResponse itemResponse(WorkflowCatalogItem i, String sourceName) {
        List<String> tags = readStringList(i.getTagsJson());
        return new WorkflowCatalogItemResponse(
                i.getId(),
                i.getSourceId(),
                sourceName,
                i.getRemoteWorkflowId(),
                i.getName(),
                i.getRemoteMode(),
                i.isActive(),
                tags,
                i.getRemoteDescription(),
                i.getDescription(),
                i.getInputSchemaJson(),
                i.getMetadataStatus().name(),
                i.getUpdatedAt());
    }

    private String writeConfig(WorkflowSourceRequest r) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(
                "executeTimeoutSeconds",
                r.executeTimeoutSeconds() == null || r.executeTimeoutSeconds() <= 0
                        ? DEFAULT_EXECUTE_TIMEOUT
                        : r.executeTimeoutSeconds());
        config.put(
                "connectTimeoutSeconds",
                r.connectTimeoutSeconds() == null || r.connectTimeoutSeconds() <= 0
                        ? DEFAULT_CONNECT_TIMEOUT
                        : r.connectTimeoutSeconds());
        try {
            return json.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to write source config", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize value", e);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String safe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    // Exposed for runtime use when lastTestedAt needs a timestamp without persisting in this path.
    Instant now() {
        return Instant.now();
    }
}
