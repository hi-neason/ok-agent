package io.okagent.module.identity.infrastructure.persistence;

import io.okagent.module.identity.domain.SecurityAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {}
