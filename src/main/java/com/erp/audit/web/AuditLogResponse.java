package com.erp.audit.web;

import com.erp.audit.domain.AuditLog;

import java.time.Instant;

/** API view of an audit row. */
public record AuditLogResponse(Long id, String eventType, String actor, Instant occurredAt,
                              String refType, String refId, String summary, String detail) {

    static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getEventType(), a.getActor(), a.getOccurredAt(),
                a.getRefType(), a.getRefId(), a.getSummary(), a.getDetail());
    }
}
