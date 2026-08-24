package io.okagent.web.identity;

import io.okagent.domain.identity.SecurityAuditEvent;
import java.time.Instant;
import java.util.UUID;

public record SecurityAuditResponse(
        UUID id,
        UUID actorId,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        String outcome,
        String details,
        Instant occurredAt) {
    public static SecurityAuditResponse from(SecurityAuditEvent event) {
        return new SecurityAuditResponse(
                event.getId(),
                event.getActorId(),
                event.getActorUsername(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getOutcome(),
                event.getDetails(),
                event.getOccurredAt());
    }
}
