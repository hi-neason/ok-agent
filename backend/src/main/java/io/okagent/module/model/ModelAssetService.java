package io.okagent.module.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelAssetService {
    private final Map<String, ModelAssetResponse> models = new LinkedHashMap<>();

    public ModelAssetService() {
        create(new ModelAssetRequest("Qwen Production", ModelType.LLM, "DashScope", "qwen-plus", "https://dashscope.aliyuncs.com/compatible-mode/v1", "secrets/models/qwen-prod", true));
        create(new ModelAssetRequest("Whisper Transcription", ModelType.SPEECH, "OpenAI", "whisper-1", "https://api.openai.com/v1", "secrets/models/openai-speech", true));
        create(new ModelAssetRequest("Invoice OCR", ModelType.OCR, "Alibaba Cloud", "ocr-invoice", "https://ocr-api.internal/v1", "secrets/models/ocr-prod", false));
    }

    public synchronized List<ModelAssetResponse> list() {
        return models.values().stream().sorted(Comparator.comparing(ModelAssetResponse::updatedAt).reversed()).toList();
    }

    public synchronized ModelAssetResponse create(ModelAssetRequest request) {
        var response = response(UUID.randomUUID().toString(), request);
        models.put(response.id(), response);
        return response;
    }

    public synchronized ModelAssetResponse update(String id, ModelAssetRequest request) {
        require(id);
        var response = response(id, request);
        models.put(id, response);
        return response;
    }

    public synchronized ModelAssetResponse setEnabled(String id, boolean enabled) {
        var current = require(id);
        var updated = new ModelAssetResponse(current.id(), current.name(), current.type(), current.provider(), current.modelId(), current.endpoint(), current.secretRef(), enabled, Instant.now());
        models.put(id, updated);
        return updated;
    }

    public synchronized void delete(String id) {
        require(id);
        models.remove(id);
    }

    public Map<String, Object> test(ModelAssetRequest request) {
        if (request.endpoint().isBlank() || request.secretRef().isBlank()) {
            return Map.of("success", false, "message", "Endpoint and SecretRef are required.");
        }
        return Map.of("success", true, "message", "Configuration validated. Network probing is disabled in the control plane.");
    }

    private ModelAssetResponse require(String id) {
        var response = models.get(id);
        if (response == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found");
        return response;
    }

    private ModelAssetResponse response(String id, ModelAssetRequest request) {
        return new ModelAssetResponse(id, request.name(), request.type(), request.provider(), request.modelId(), request.endpoint(), request.secretRef(), request.enabled(), Instant.now());
    }
}
