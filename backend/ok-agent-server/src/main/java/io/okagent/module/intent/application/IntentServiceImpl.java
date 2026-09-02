package io.okagent.module.intent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.okagent.module.intent.domain.Intent;
import io.okagent.module.intent.infrastructure.persistence.IntentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IntentServiceImpl implements IntentService {
    private static final Logger log = LoggerFactory.getLogger(IntentServiceImpl.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final IntentRepository intents;
    private final ObjectMapper json;

    public IntentServiceImpl(IntentRepository intents, ObjectMapper json) {
        this.intents = intents;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntentNode> getTree() {
        List<Intent> all = intents.findAll();
        Map<UUID, List<Intent>> byParent =
                all.stream().filter(i -> i.getParentId() != null).collect(Collectors.groupingBy(Intent::getParentId));
        List<Intent> roots = intents.findByParentIdIsNullOrderByNameAsc();
        return roots.stream().map(r -> toNode(r, byParent)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IntentDto get(UUID id) {
        return toDto(find(id));
    }

    @Override
    @Transactional
    public IntentDto create(CreateIntentRequest request) {
        if (request.intentKey() == null || request.intentKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "intentKey is required");
        }
        if (intents.findByIntentKey(request.intentKey().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "intentKey already exists");
        }
        if (request.parentId() != null && !intents.existsById(request.parentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent intent does not exist");
        }
        var intent = new Intent(
                UUID.randomUUID(),
                request.intentKey().trim(),
                request.name() == null
                        ? request.intentKey().trim()
                        : request.name().trim(),
                request.parentId());
        intent.applyDefinition(
                intent.getName(), request.description(), writeExamples(request.examples()), request.sortOrder());
        return toDto(intents.save(intent));
    }

    @Override
    @Transactional
    public IntentDto update(UUID id, UpdateIntentRequest request) {
        var intent = find(id);
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "an intent cannot be its own parent");
            }
            if (!intents.existsById(request.parentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent intent does not exist");
            }
        }
        intent.applyDefinition(
                request.name() == null ? intent.getName() : request.name().trim(),
                request.description(),
                writeExamples(request.examples()),
                request.sortOrder());
        intent.moveUnder(request.parentId());
        return toDto(intents.save(intent));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var intent = find(id);
        if (intents.countByParentId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot delete an intent that still has children");
        }
        intents.delete(intent);
    }

    private IntentNode toNode(Intent intent, Map<UUID, List<Intent>> byParent) {
        List<IntentNode> children = byParent.getOrDefault(intent.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(Intent::getSortOrder).thenComparing(Intent::getName))
                .map(c -> toNode(c, byParent))
                .toList();
        return new IntentNode(toDto(intent), children);
    }

    private IntentDto toDto(Intent i) {
        return new IntentDto(
                i.getId(),
                i.getParentId(),
                i.getIntentKey(),
                i.getName(),
                i.getDescription(),
                readExamples(i.getExamplesJson()),
                i.getSortOrder(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }

    private Intent find(UUID id) {
        return intents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Intent not found"));
    }

    private List<String> readExamples(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return json.readValue(raw, STRING_LIST);
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String writeExamples(List<String> examples) {
        if (examples == null || examples.isEmpty()) {
            return "[]";
        }
        try {
            return json.writeValueAsString(examples);
        } catch (Exception e) {
            return "[]";
        }
    }
}
