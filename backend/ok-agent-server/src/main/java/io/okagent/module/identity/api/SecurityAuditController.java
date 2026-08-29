package io.okagent.module.identity.api;

import io.okagent.module.identity.application.*;
import io.okagent.module.identity.application.SecurityAuditService;
import io.okagent.shared.api.ApiResponse;
import io.okagent.shared.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security-audit")
@Validated
public class SecurityAuditController {
    private final SecurityAuditService auditService;

    public SecurityAuditController(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    /** Lists append-only security-administration events for administrator review. */
    @GetMapping
    public ApiResponse<PageResponse<SecurityAuditResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.of(auditService.list(page, size)));
    }
}
