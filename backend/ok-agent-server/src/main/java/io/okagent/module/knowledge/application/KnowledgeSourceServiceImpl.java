package io.okagent.module.knowledge.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.knowledge.domain.KnowledgeCatalogItem;
import io.okagent.module.knowledge.domain.KnowledgeMetadataStatus;
import io.okagent.module.knowledge.domain.KnowledgeSource;
import io.okagent.module.knowledge.infrastructure.persistence.KnowledgeCatalogItemRepository;
import io.okagent.module.knowledge.infrastructure.persistence.KnowledgeSourceRepository;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.knowledge.application.KnowledgeCatalogItemResponse;
import io.okagent.module.knowledge.application.KnowledgeSourceRequest;
import io.okagent.module.knowledge.application.KnowledgeSourceResponse;
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
public class KnowledgeSourceServiceImpl implements KnowledgeSourceService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSourceServiceImpl.class);
    private static final int DEFAULT_RETRIEVE_TIMEOUT = 30;
    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    private final KnowledgeSourceRepository sources;
    private final KnowledgeCatalogItemRepository items;
    private final ApiKeyCipher cipher;
    private final List<KnowledgeProvider> providers;
    private final ObjectMapper json = new ObjectMapper();

    public KnowledgeSourceServiceImpl(
            KnowledgeSourceRepository sources,
            KnowledgeCatalogItemRepository items,
            ApiKeyCipher cipher,
            List<KnowledgeProvider> providers) {
        this.sources = sources;
        this.items = items;
        this.cipher = cipher;
        this.providers = providers;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KnowledgeSourceResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return sources.findAll(pageable).map(this::response);
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeSourceResponse get(UUID id) {
        return response(find(id));
    }

    @Override
    @Transactional
    public KnowledgeSourceResponse create(KnowledgeSourceRequest r) {
        if (sources.existsBySourceKey(r.sourceKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Knowledge source key already exists");
        }
        if (r.apiKey() == null || r.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key is required");
        }
        var source = new KnowledgeSource(
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
    public KnowledgeSourceResponse update(UUID id, KnowledgeSourceRequest r) {
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
    public KnowledgeSourceResponse setEnabled(UUID id, boolean enabled) {
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
    public KnowledgeSourceResponse test(UUID id) {
        var source = find(id);
        var result = provider(source).test(toConfig(source));
        source.recordTest(result.success() && result.supported(), result.message());
        sources.save(source);
        return response(source);
    }

    @Override
    public KnowledgeSourceResponse testDraft(KnowledgeSourceRequest r) {
        var source = new KnowledgeSource(
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
    public List<KnowledgeCatalogItemResponse> sync(UUID id) {
        var source = find(id);
        var provider = provider(source);
        var config = toConfig(source);

        List<RemoteKnowledgeSummary> remote;
        try {
            remote = provider.listKnowledgeBases(config);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list knowledge bases: " + safe(e));
        }

        var existing = new LinkedHashMap<String, KnowledgeCatalogItem>();
        for (var item : items.findBySourceId(id)) {
            existing.put(item.getRemoteKnowledgeId(), item);
        }

        List<KnowledgeCatalogItem> saved = new ArrayList<>();
        for (var summary : remote) {
            var previous = existing.get(summary.remoteKnowledgeId());
            String tagsJson = write(summary.tags() == null ? List.of() : summary.tags());
            String rawJson = write(Map.of(
                    "id", summary.remoteKnowledgeId(),
                    "name", summary.name(),
                    "document_count", summary.documentCount(),
                    "word_count", summary.wordCount()));
            if (previous == null) {
                var item = new KnowledgeCatalogItem(
                        UUID.randomUUID(),
                        id,
                        summary.remoteKnowledgeId(),
                        summary.name(),
                        summary.documentCount(),
                        summary.wordCount(),
                        summary.active(),
                        tagsJson,
                        summary.remoteDescription() == null ? "" : summary.remoteDescription(),
                        "",
                        rawJson,
                        KnowledgeMetadataStatus.NEEDS_REVIEW);
                saved.add(items.save(item));
            } else {
                previous.applyRemoteUpdate(
                        summary.name(),
                        summary.documentCount(),
                        summary.wordCount(),
                        summary.active(),
                        tagsJson,
                        summary.remoteDescription() == null ? "" : summary.remoteDescription(),
                        rawJson);
                saved.add(items.save(previous));
            }
        }
        source.recordSynced(saved.size());
        sources.save(source);
        return saved.stream().map(i -> itemResponse(i, source.getName())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeCatalogItemResponse> catalogItems(UUID sourceId) {
        var sourceName =
                sources.findById(sourceId).map(KnowledgeSource::getName).orElse("");
        return items.findBySourceId(sourceId).stream()
                .sorted(Comparator.comparing(KnowledgeCatalogItem::getName))
                .map(i -> itemResponse(i, sourceName))
                .toList();
    }

    @Override
    @Transactional
    public KnowledgeCatalogItemResponse updateCatalogDescription(UUID itemId, String description) {
        var item = items.findById(itemId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge catalog item not found"));
        var text = description == null ? "" : description.trim();
        item.updateMetadata(
                text, text.isBlank() ? KnowledgeMetadataStatus.NEEDS_REVIEW : KnowledgeMetadataStatus.READY);
        var source = sources.findById(item.getSourceId())
                .map(KnowledgeSource::getName)
                .orElse("");
        return itemResponse(items.save(item), source);
    }

    /** Builds a decrypted runtime config for a persisted source; package-access for the runtime catalog. */
    KnowledgeSourceConfig toConfig(KnowledgeSource source) {
        Map<String, Object> config = readMap(source.getConfigJson());
        Map<String, Object> secrets = new LinkedHashMap<>();
        if (source.getSecretsCiphertext() != null
                && !source.getSecretsCiphertext().isBlank()) {
            secrets.put("apiKey", cipher.decrypt(source.getSecretsCiphertext()));
        }
        int retrieveTimeout = DEFAULT_RETRIEVE_TIMEOUT;
        int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        Object rt = config.get("retrieveTimeoutSeconds");
        if (rt instanceof Number n) retrieveTimeout = n.intValue();
        Object ct = config.get("connectTimeoutSeconds");
        if (ct instanceof Number n) connectTimeout = n.intValue();
        return new KnowledgeSourceConfig(
                source.getId().toString(),
                source.getSourceType().name(),
                source.getBaseUrl(),
                config,
                secrets,
                retrieveTimeout,
                connectTimeout);
    }

    private KnowledgeProvider provider(KnowledgeSource source) {
        return providers.stream()
                .filter(p -> p.type().equalsIgnoreCase(source.getSourceType().name()))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("No knowledge provider for type " + source.getSourceType()));
    }

    private KnowledgeSource find(UUID id) {
        return sources.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
    }

    private KnowledgeSourceResponse response(KnowledgeSource s) {
        return response(s, "SUCCESS".equals(s.getLastTestStatus()), s.getLastTestMessage());
    }

    private KnowledgeSourceResponse response(KnowledgeSource s, boolean testPassed, String message) {
        var config = readMap(s.getConfigJson());
        int retrieve =
                config.get("retrieveTimeoutSeconds") instanceof Number n ? n.intValue() : DEFAULT_RETRIEVE_TIMEOUT;
        int connect = config.get("connectTimeoutSeconds") instanceof Number n ? n.intValue() : DEFAULT_CONNECT_TIMEOUT;
        return new KnowledgeSourceResponse(
                s.getId(),
                s.getSourceKey(),
                s.getName(),
                s.getSourceType(),
                s.getBaseUrl(),
                s.isEnabled(),
                s.getSecretsCiphertext() != null && !s.getSecretsCiphertext().isBlank(),
                retrieve,
                connect,
                testPassed ? "SUCCESS" : (s.getLastTestStatus() == null ? "UNTESTED" : s.getLastTestStatus()),
                message == null ? "" : message,
                s.getLastTestedAt(),
                s.getLastSyncedAt(),
                s.getKnowledgeCount(),
                s.getUpdatedAt());
    }

    private KnowledgeCatalogItemResponse itemResponse(KnowledgeCatalogItem i, String sourceName) {
        List<String> tags = readStringList(i.getTagsJson());
        return new KnowledgeCatalogItemResponse(
                i.getId(),
                i.getSourceId(),
                sourceName,
                i.getRemoteKnowledgeId(),
                i.getName(),
                i.isActive(),
                tags,
                i.getRemoteDescription(),
                i.getDescription(),
                i.getDocumentCount(),
                i.getWordCount(),
                i.getMetadataStatus().name(),
                i.getUpdatedAt());
    }

    private String writeConfig(KnowledgeSourceRequest r) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(
                "retrieveTimeoutSeconds",
                r.retrieveTimeoutSeconds() == null || r.retrieveTimeoutSeconds() <= 0
                        ? DEFAULT_RETRIEVE_TIMEOUT
                        : r.retrieveTimeoutSeconds());
        config.put(
                "connectTimeoutSeconds",
                r.connectTimeoutSeconds() == null || r.connectTimeoutSeconds() <= 0
                        ? DEFAULT_CONNECT_TIMEOUT
                        : r.connectTimeoutSeconds());
        try {
            return json.writeValueAsString(config);
        } catch (Exception e) {
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
}
