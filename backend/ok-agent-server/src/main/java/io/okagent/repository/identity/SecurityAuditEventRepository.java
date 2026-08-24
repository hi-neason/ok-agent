package io.okagent.repository.identity;

import io.okagent.domain.identity.SecurityAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {}
