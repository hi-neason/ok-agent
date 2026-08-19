package io.okagent.service.intent;

import java.util.List;
import java.util.UUID;

public record UpdateIntentRequest(
        String name, UUID parentId, String description, List<String> examples, int sortOrder) {}
