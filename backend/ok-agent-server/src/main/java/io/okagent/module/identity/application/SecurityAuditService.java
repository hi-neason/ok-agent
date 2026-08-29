package io.okagent.module.identity.application;

import io.okagent.module.identity.application.SecurityAuditResponse;
import org.springframework.data.domain.Page;

public interface SecurityAuditService {
    /** Appends a successful security-administration event without credential material. */
    void record(AuthenticatedActor actor, String action, String targetType, String targetId, String details);

    /** Lists the newest security-administration events for administrator review. */
    Page<SecurityAuditResponse> list(int page, int size);
}
