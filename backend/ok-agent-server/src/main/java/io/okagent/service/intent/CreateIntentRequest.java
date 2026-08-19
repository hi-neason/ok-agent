package io.okagent.service.intent;

import java.util.List;
import java.util.UUID;

public record CreateIntentRequest(
        String intentKey, String name, UUID parentId, String description, List<String> examples, int sortOrder) {}
