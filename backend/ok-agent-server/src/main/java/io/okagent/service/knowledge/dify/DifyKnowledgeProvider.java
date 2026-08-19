package io.okagent.service.knowledge.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.okagent.service.knowledge.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Dify knowledge-base (dataset) provider.
 *
 * <p>A Dify dataset-level API key ({@code dataset-...}) is workspace-scoped, so one source can list
 * and retrieve from many datasets: {@code GET /datasets} enumerates them and
 * {@code POST /datasets/{id}/retrieve} performs semantic retrieval. Dify datasets have no
 * active/inactive flag, so discovered bases are treated as active. On retrieval we first fetch the
 * dataset's own {@code retrieval_model_dict} (cached 5 minutes) and use it as the base payload so the
 * {@code search_method} matches how the dataset was indexed — {@code economy} datasets are
 * keyword/inverted-index only and forcing {@code semantic_search} on them returns "Collection not
 * found". The caller's top_k / score threshold are overlaid on top. The query is truncated to Dify's
 * 250-character limit.
 */
@Component
public class DifyKnowledgeProvider implements KnowledgeProvider {
    private static final Logger log = LoggerFactory.getLogger(DifyKnowledgeProvider.class);
    private static final int PAGE_SIZE = 100;
    private static final int DEFAULT_TOP_K = 5;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Cache of each dataset's own {@code retrieval_model_dict}, keyed by
     * {@code <baseUrl>::<datasetId>}. We must send a retrieval_model whose
     * {@code search_method} matches how the dataset was actually indexed: an
     * {@code economy} dataset only has a keyword/inverted index, so hardcoding
     * {@code semantic_search} makes Dify look for a vector collection that does
     * not exist ("Collection not found"). Fetching the dataset config and using
     * its retrieval_model as the base works for both high-quality (vector/hybrid)
     * and economy (keyword/inverted-index) datasets. Cached for 5 minutes to
     * avoid an extra HTTP round-trip on every chat turn.
     */
    private static final Duration DATASET_CONFIG_TTL = Duration.ofMinutes(5);

    private final Map<String, CacheEntry> datasetConfigCache = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "DIFY";
    }

    @Override
    public ConnectionTestResult test(KnowledgeSourceConfig config) {
        try {
            JsonNode body = getJson(config, "/datasets?page=1&limit=1");
            int total = body.path("total").isNumber() ? body.path("total").asInt() : 0;
            return ConnectionTestResult.ok("Connected to Dify datasets (" + total + " knowledge base(s) accessible)");
        } catch (Exception e) {
            return ConnectionTestResult.failed(safeMessage(e));
        }
    }

    @Override
    public List<RemoteKnowledgeSummary> listKnowledgeBases(KnowledgeSourceConfig config) {
        try {
            List<RemoteKnowledgeSummary> result = new ArrayList<>();
            int page = 1;
            while (true) {
                JsonNode body = getJson(config, "/datasets?page=" + page + "&limit=" + PAGE_SIZE);
                JsonNode data = body.path("data");
                if (data.isArray()) {
                    for (JsonNode ds : data) {
                        result.add(new RemoteKnowledgeSummary(
                                ds.path("id").asText(""),
                                ds.path("name").asText("Dify dataset"),
                                true,
                                readTags(ds),
                                ds.path("description").asText(""),
                                ds.path("document_count").asInt(0),
                                ds.path("word_count").asLong(0)));
                    }
                }
                boolean hasMore = body.path("has_more").asBoolean(false);
                if (!hasMore || !data.isArray() || data.isEmpty()) break;
                page++;
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Dify datasets: " + safeMessage(e), e);
        }
    }

    @Override
    public List<RetrievedChunk> retrieve(
            KnowledgeSourceConfig config,
            String remoteKnowledgeId,
            String query,
            Integer topK,
            Double scoreThreshold,
            String endUserId) {
        try {
            int k = topK == null || topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, 50);

            // Start from the dataset's OWN retrieval_model_dict so the search_method matches how
            // the dataset was indexed. Economy datasets only have a keyword/inverted index and no
            // vector collection — forcing semantic_search there yields "Collection not found".
            // We then overlay the caller's top_k / score threshold. reranking_enable,
            // reranking_model, weights and search_method are preserved from the dataset config
            // (the dataset controls whether a rerank model is available).
            ObjectNode retrievalModel = resolveRetrievalModel(config, remoteKnowledgeId);
            retrievalModel.put("top_k", k);
            if (scoreThreshold != null && scoreThreshold >= 0 && scoreThreshold <= 1) {
                retrievalModel.put("score_threshold_enabled", true);
                retrievalModel.put("score_threshold", scoreThreshold);
            } else {
                retrievalModel.put("score_threshold_enabled", false);
                retrievalModel.putNull("score_threshold");
            }
            // reranking_enable is a required field; if the dataset config did not supply one,
            // default to disabled (bindings do not expose a separate rerank model).
            if (!retrievalModel.has("reranking_enable")) {
                retrievalModel.put("reranking_enable", false);
            }
            if (!retrievalModel.has("reranking_model")) {
                ObjectNode rerankingModel = json.createObjectNode();
                rerankingModel.put("reranking_provider_name", "");
                rerankingModel.put("reranking_model_name", "");
                retrievalModel.set("reranking_model", rerankingModel);
            }

            // Dify caps the query at 250 characters; truncate rather than risk a hard 400 on
            // long prompts (the first 250 chars still carry the primary retrieval intent).
            String q = query == null ? "" : query;
            if (q.length() > 250) {
                log.debug(
                        "Dify retrieve query truncated from {} to 250 chars for dataset {}",
                        q.length(),
                        remoteKnowledgeId);
                q = q.substring(0, 250);
            }
            ObjectNode body = json.createObjectNode();
            body.put("query", q);
            body.set("retrieval_model", retrievalModel);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl(config) + "/datasets/" + remoteKnowledgeId + "/retrieve"))
                    .timeout(Duration.ofSeconds(config.retrieveTimeoutSeconds()))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.AUTHORIZATION, bearer(config))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "HTTP " + response.statusCode() + " from Dify retrieve: " + truncate(response.body(), 300));
            }
            JsonNode root = json.readTree(response.body());
            JsonNode records = root.path("records");
            List<RetrievedChunk> chunks = new ArrayList<>();
            if (records.isArray()) {
                for (JsonNode record : records) {
                    JsonNode segment = record.path("segment");
                    String content = segment.path("content").asText("");
                    if (content.isBlank()) continue;
                    Double score = record.has("score") && !record.path("score").isNull()
                            ? record.path("score").asDouble()
                            : null;
                    chunks.add(new RetrievedChunk(
                            content,
                            segment.path("document").path("name").asText(""),
                            segment.path("id").asText(""),
                            score));
                }
            }
            return chunks;
        } catch (Exception e) {
            log.warn("Dify retrieve failed for dataset {}: {}", remoteKnowledgeId, e.getMessage());
            throw new IllegalStateException("Failed to retrieve from Dify dataset: " + safeMessage(e), e);
        }
    }

    /**
     * Returns the dataset's own {@code retrieval_model_dict} as a mutable deep copy, using a
     * short-TTL cache. Falls back to a minimal {@code semantic_search} config if the dataset
     * details cannot be loaded (so retrieval is still attempted, with a logged warning).
     */
    private ObjectNode resolveRetrievalModel(KnowledgeSourceConfig config, String remoteKnowledgeId) {
        String cacheKey = baseUrl(config) + "::" + remoteKnowledgeId;
        CacheEntry cached = datasetConfigCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.fetchedAt.plus(DATASET_CONFIG_TTL).isAfter(now)) {
            return cached.model.deepCopy();
        }
        try {
            JsonNode dataset = getJson(config, "/datasets/" + remoteKnowledgeId);
            JsonNode dict = dataset.path("retrieval_model_dict");
            ObjectNode model;
            if (dict.isObject()) {
                model = ((ObjectNode) dict).deepCopy();
            } else {
                model = json.createObjectNode();
                model.put("search_method", "keyword_search");
                log.warn(
                        "Dify dataset {} returned no retrieval_model_dict (indexing_technique={}); "
                                + "falling back to keyword_search",
                        remoteKnowledgeId,
                        dataset.path("indexing_technique").asText("null"));
            }
            if (!model.has("search_method")
                    || model.path("search_method").asText("").isBlank()) {
                model.put("search_method", "keyword_search");
            }
            datasetConfigCache.put(cacheKey, new CacheEntry(model.deepCopy(), now));
            return model;
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch Dify dataset {} config, falling back to keyword_search: {}",
                    remoteKnowledgeId,
                    safeMessage(e));
            ObjectNode fallback = json.createObjectNode();
            fallback.put("search_method", "keyword_search");
            return fallback;
        }
    }

    private record CacheEntry(ObjectNode model, Instant fetchedAt) {}

    private JsonNode getJson(KnowledgeSourceConfig config, String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(config) + path))
                .timeout(Duration.ofSeconds(Math.max(config.connectTimeoutSeconds(), 5)))
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.AUTHORIZATION, bearer(config))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "HTTP " + response.statusCode() + " from Dify: " + truncate(response.body(), 300));
        }
        return json.readTree(response.body());
    }

    private List<String> readTags(JsonNode dataset) {
        var tags = dataset.path("tags");
        if (!tags.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        tags.forEach(t -> result.add(t.asText()));
        return result;
    }

    private String bearer(KnowledgeSourceConfig config) {
        String key = config.secret("apiKey");
        if (key.isBlank()) throw new IllegalStateException("Dify dataset API key is not configured");
        return "Bearer " + key;
    }

    private String baseUrl(KnowledgeSourceConfig config) {
        String base = config.baseUrl() == null ? "" : config.baseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.isEmpty()) throw new IllegalStateException("Dify base URL is not configured");
        return base;
    }

    private String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "...(truncated)";
    }

    private String safeMessage(Exception e) {
        var root = e;
        while (root.getCause() instanceof Exception cause && cause != root) root = cause;
        String message = root.getMessage();
        if (message == null || message.isBlank()) return root.getClass().getSimpleName();
        if (message.contains("401") || message.toLowerCase().contains("unauthorized")) {
            return "Authentication failed: check the Dify dataset API key";
        }
        return message;
    }
}
