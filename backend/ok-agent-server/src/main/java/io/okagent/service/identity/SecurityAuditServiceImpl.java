package io.okagent.service.identity;

import io.okagent.domain.identity.SecurityAuditEvent;
import io.okagent.repository.identity.SecurityAuditEventRepository;
import io.okagent.web.identity.SecurityAuditResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {
    private final SecurityAuditEventRepository repository;

    public SecurityAuditServiceImpl(SecurityAuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(AuthenticatedActor actor, String action, String targetType, String targetId, String details) {
        repository.save(new SecurityAuditEvent(
                UUID.randomUUID(),
                actor.accountId(),
                actor.username(),
                action,
                targetType,
                targetId,
                "SUCCESS",
                details == null ? "" : details,
                Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SecurityAuditResponse> list(int page, int size) {
        return repository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(SecurityAuditResponse::from);
    }
}
