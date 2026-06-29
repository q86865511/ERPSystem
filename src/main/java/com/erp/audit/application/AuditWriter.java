package com.erp.audit.application;

import com.erp.audit.domain.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an audit row in its own new transaction, so the audit write is independent of the business
 * transaction that triggered it (the listener catches any failure). Separating the {@code REQUIRES_NEW}
 * boundary from the listener's try/catch lets a failed write be swallowed without a rollback-only leak.
 */
@Service
public class AuditWriter {

    private final AuditLogRepository repository;

    public AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        repository.save(entry);
    }
}
