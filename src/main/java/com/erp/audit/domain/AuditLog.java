package com.erp.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

/**
 * One append-only audit record. Written by {@code AuditEventListener} after a sensitive action commits;
 * the DB enforces append-only (no update/delete). Structured columns drive filtering; {@code summary} is
 * the human-readable line and {@code detail} an optional extra.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private String refId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(length = 2000)
    private String detail;

    public AuditLog(String eventType, String actor, String refType, String refId, String summary,
                    String detail) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        this.eventType = eventType;
        this.actor = actor == null || actor.isBlank() ? "system" : actor;
        this.refType = refType;
        this.refId = refId;
        this.summary = summary == null ? "" : summary;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }
}
