package com.erp.audit.application;

import com.erp.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByEventType(String eventType);

    List<AuditLog> findByRefTypeAndRefId(String refType, String refId);

    /** Paged audit search; each filter is applied only when non-null. */
    @Query("""
            select a from AuditLog a
            where (:eventType is null or a.eventType = :eventType)
              and (:actor is null or a.actor = :actor)
            """)
    Page<AuditLog> search(@Param("eventType") String eventType, @Param("actor") String actor,
                          Pageable pageable);
}
