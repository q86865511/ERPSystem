package com.erp.observation.application;

import com.erp.iam.api.AuthAuditEvent;
import com.erp.ledger.api.FiscalPeriodChangedEvent;
import com.erp.ledger.api.JournalPostedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emits business metrics from the same domain events the audit trail listens to. Counts only committed
 * actions ({@code AFTER_COMMIT}); {@code fallbackExecution=true} is required because login events fire
 * outside a transaction. Tags are strictly bounded (document type, success/failure, close/reopen) — never
 * actor/username/document-id, which would explode meter-registry cardinality. Like the audit listener, it
 * consumes only published {@code *.api} event types, so module boundaries stay ArchUnit-clean.
 */
@Component
public class MetricsEventListener {

    private static final Logger log = LoggerFactory.getLogger(MetricsEventListener.class);

    private final MeterRegistry meters;

    public MetricsEventListener(MeterRegistry meters) {
        this.meters = meters;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onJournalPosted(JournalPostedEvent e) {
        try {
            String sourceDocType = e.sourceDocType() != null ? e.sourceDocType() : "UNKNOWN";
            // No ".total" suffix: Micrometer appends "_total" to counters in the Prometheus exposition,
            // so "erp.journal.postings" renders as "erp_journal_postings_total".
            meters.counter("erp.journal.postings", "sourceDocType", sourceDocType).increment();
        } catch (RuntimeException ex) {
            log.warn("metrics: journal-posted counter failed", ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuth(AuthAuditEvent e) {
        try {
            meters.counter("erp.auth.logins", "result", e.success() ? "success" : "failure").increment();
        } catch (RuntimeException ex) {
            log.warn("metrics: login counter failed", ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFiscalPeriodChanged(FiscalPeriodChangedEvent e) {
        try {
            String action = "OPEN".equalsIgnoreCase(e.newStatus()) ? "reopened" : "closed";
            meters.counter("erp.fiscal.period.changes", "action", action).increment();
        } catch (RuntimeException ex) {
            log.warn("metrics: fiscal-period counter failed", ex);
        }
    }
}
