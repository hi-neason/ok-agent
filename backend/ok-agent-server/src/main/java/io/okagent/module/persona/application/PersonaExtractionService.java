package io.okagent.module.persona.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.agent.domain.AgentAsset;
import io.okagent.module.conversation.domain.DialogueTurn;
import io.okagent.module.model.domain.ModelAsset;
import io.okagent.module.persona.domain.UserPersona;
import io.okagent.module.agent.infrastructure.persistence.AgentAssetRepository;
import io.okagent.module.model.infrastructure.persistence.ModelAssetRepository;
import io.okagent.module.persona.infrastructure.persistence.UserPersonaRepository;
import io.okagent.module.conversation.application.DialogueService;
import io.okagent.module.model.application.ApiKeyCipher;
import io.okagent.module.persona.application.UpsertPersonaRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Path-A automatic persona extraction pipeline: after a conversation turn, asynchronously asks the
 * agent's bound LLM to extract stable user facts / preferences / summary from the recent transcript,
 * then upserts the structured persona and appends the free-form memory delta.
 *
 * <p>This is independent of the harness's own agent-memory mechanism ({@code memory/}): it targets
 * the user dimension ({@code users/{userId}/persona}) and never touches the agent's working memory.
 *
 * <p>Best-effort by design: failures are logged and never propagate to the chat response. A
 * per-user throttle prevents repeated LLM calls within a short window.
 */
@Service
public class PersonaExtractionService {
    private static final Logger log = LoggerFactory.getLogger(PersonaExtractionService.class);

    private static final Duration THROTTLE = Duration.ofMinutes(30);
    private static final int MAX_TURNS = 20;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);

    private final AgentAssetRepository agents;
    private final ModelAssetRepository models;
    private final ApiKeyCipher cipher;
    private final DialogueService dialogue;
    private final UserPersonaRepository personas;
    private final UserPersonaService personaService;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "persona-extract-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    public PersonaExtractionService(
            AgentAssetRepository agents,
            ModelAssetRepository models,
            ApiKeyCipher cipher,
            DialogueService dialogue,
            UserPersonaRepository personas,
            UserPersonaService personaService) {
        this.agents = agents;
        this.models = models;
        this.cipher = cipher;
        this.dialogue = dialogue;
        this.personas = personas;
        this.personaService = personaService;
    }

    /** Fires extraction asynchronously after a chat round; no-op when the agent has extraction disabled. */
    public void extractAsync(UUID agentId, String userId, String sessionId) {
        if (userId == null || userId.isBlank()) return;
        AgentAsset agent = agents.findById(agentId).orElse(null);
        if (agent == null || !agent.isPersonaExtractEnabled()) return;
        if (agent.getModelAssetId() == null) return;
        executor.submit(() -> {
            try {
                runExtraction(agent, userId, sessionId);
            } catch (Exception e) {
                log.warn("Persona extraction failed for agent={}, userId={}: {}", agentId, userId, e.getMessage());
            }
        });
    }

    private void runExtraction(AgentAsset agent, String userId, String sessionId) {
        UUID agentId = agent.getId();
        UserPersona persona =
                personas.findByIdUserIdAndIdAgentId(userId, agentId).orElse(null);
        if (persona != null
                && persona.getLastExtractedAt() != null
                && Duration.between(persona.getLastExtractedAt(), Instant.now()).compareTo(THROTTLE) < 0) {
            log.debug("Persona extraction throttled for agent={}, userId={}", agentId, userId);
            return;
        }

        List<DialogueTurn> turns = dialogue.getMessages(sessionId);
        if (turns.isEmpty()) return;
        int from = Math.max(0, turns.size() - MAX_TURNS);
        List<DialogueTurn> recent = turns.subList(from, turns.size());

        ModelAsset model = models.findById(agent.getModelAssetId()).orElse(null);
        if (model == null || !model.isEnabled()) return;

        String transcript = renderTranscript(recent);
        String existing = renderExisting(persona);
        String extraction = callLlm(model, buildPrompt(transcript, existing));
        if (extraction == null || extraction.isBlank()) return;

        applyExtraction(userId, agentId, extraction);
    }

    private String renderTranscript(List<DialogueTurn> turns) {
        StringBuilder sb = new StringBuilder();
        for (DialogueTurn t : turns) {
            if ("error".equals(t.getRole())) continue;
            sb.append(t.getRole()).append(": ").append(t.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String renderExisting(UserPersona persona) {
        if (persona == null) return "(none)";
        StringBuilder sb = new StringBuilder();
        if (persona.getSummary() != null)
            sb.append("summary: ").append(persona.getSummary()).append('\n');
        if (persona.getTagsJson() != null)
            sb.append("tags: ").append(persona.getTagsJson()).append('\n');
        if (persona.getPreferencesJson() != null)
            sb.append("preferences: ").append(persona.getPreferencesJson()).append('\n');
        if (persona.getFacts() != null)
            sb.append("facts: ").append(persona.getFacts()).append('\n');
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private String buildPrompt(String transcript, String existing) {
        return """
                你是一个用户画像分析助手。请从下面的对话中抽取关于该用户的**稳定、长期有效**的信息，更新已有画像。
                只提取真正能帮助未来对话的用户特征（身份、角色、偏好、禁忌、长期目标、关键事实），忽略一次性的临时话题。

                已有画像：
                %s

                最近对话：
                %s

                请只输出一个 JSON 对象，字段如下（没有新信息就留空字符串或空数组，保留已有信息）：
                {
                  "summary": "一句话概括这个用户",
                  "tags": ["标签1","标签2"],
                  "preferences": {"偏好项": "偏好值"},
                  "facts": "关键事实，分号分隔",
                  "memoryDelta": "一段自然语言的长期记忆增量（会追加到用户 MEMORY.md），没有则留空"
                }
                """
                .formatted(existing, transcript);
    }

    private String callLlm(ModelAsset model, String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.getModelId());
            body.put("temperature", 0.2);
            body.put("max_tokens", 1000);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", "你是严谨的信息抽取助手，只输出 JSON。"),
                    Map.of("role", "user", "content", prompt));
            body.put("messages", messages);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl(model.getEndpoint())))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cipher.decrypt(model.getApiKeyCiphertext()))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("LLM extraction returned HTTP {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode root = json.readTree(resp.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return null;
            return choices.get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("LLM extraction call failed: {}", e.getMessage());
            return null;
        }
    }

    private static String chatCompletionsUrl(String endpoint) {
        String base = endpoint == null ? "" : endpoint.trim();
        if (base.endsWith("/chat/completions")) return base;
        if (base.endsWith("/")) return base + "chat/completions";
        return base + "/chat/completions";
    }

    private void applyExtraction(String userId, UUID agentId, String raw) {
        try {
            String jsonText = stripCodeFences(raw);
            JsonNode node = json.readTree(jsonText);
            List<String> tags = readStringList(node.path("tags"));
            Map<String, String> prefs = readStringMap(node.path("preferences"));
            String facts = textOrNull(node.path("facts"));
            String summary = textOrNull(node.path("summary"));
            String memoryDelta = textOrNull(node.path("memoryDelta"));

            personaService.upsert(userId, agentId, new UpsertPersonaRequest(tags, prefs, facts, summary));
            if (memoryDelta != null) {
                personaService.appendMemory(userId, agentId, memoryDelta);
            }
            personas.findByIdUserIdAndIdAgentId(userId, agentId).ifPresent(p -> {
                p.setLastExtractedAt(Instant.now());
                personas.save(p);
            });
            log.info(
                    "Persona extracted for agent={}, userId={} (tags={}, facts_len={})",
                    agentId,
                    userId,
                    tags.size(),
                    facts == null ? 0 : facts.length());
        } catch (Exception e) {
            log.warn("Failed to apply persona extraction: {}", e.getMessage());
        }
    }

    private static String stripCodeFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.strip();
    }

    private List<String> readStringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> {
                String v = n.asText("").trim();
                if (!v.isEmpty()) out.add(v);
            });
        }
        return out;
    }

    private Map<String, String> readStringMap(JsonNode obj) {
        Map<String, String> out = new LinkedHashMap<>();
        if (obj != null && obj.isObject()) {
            obj.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText("")));
        }
        return out;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String s = n.asText("").trim();
        return s.isEmpty() ? null : s;
    }
}
