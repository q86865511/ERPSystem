package com.erp.audit.web;

import com.erp.audit.application.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only audit viewer. {@code GET /api/audit} is ADMIN-only (see SecurityConfig); paged, newest first,
 * optionally filtered by event type and actor.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return repository.search(blankToNull(eventType), blankToNull(actor), pageable)
                .map(AuditLogResponse::from);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
