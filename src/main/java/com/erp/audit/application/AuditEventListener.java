package com.erp.audit.application;

import com.erp.assistant.api.AssistantToolExecutedEvent;
import com.erp.audit.domain.AuditLog;
import com.erp.iam.api.AuthAuditEvent;
import com.erp.ledger.api.FiscalPeriodChangedEvent;
import com.erp.ledger.api.JournalPostedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the audit trail from sensitive-action events. Uses {@code AFTER_COMMIT} so a row is recorded only
 * for an action that actually committed (no phantom rows for rolled-back work); {@code fallbackExecution}
 * also handles events published outside a transaction (e.g. login). Audit failures are caught and logged —
 * the audit trail must never break the business action it observes.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditWriter writer;

    public AuditEventListener(AuditWriter writer) {
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onJournalPosted(JournalPostedEvent e) {
        String source = e.sourceDocType() != null ? " (" + e.sourceDocType() + "/" + e.sourceDocId() + ")" : "";
        save(new AuditLog("JOURNAL_POSTED", e.actor(), "JOURNAL_ENTRY",
                e.entryNo() != null ? String.valueOf(e.entryNo()) : null,
                "Posted journal entry #" + e.entryNo() + source,
                "postingDate=" + e.postingDate() + (e.memo() != null ? "; memo=" + e.memo() : "")));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFiscalPeriodChanged(FiscalPeriodChangedEvent e) {
        boolean reopened = "OPEN".equalsIgnoreCase(e.newStatus());
        String period = e.yearCode() + "/" + e.periodNo();
        save(new AuditLog(reopened ? "PERIOD_REOPENED" : "PERIOD_CLOSED", e.actor(), "FISCAL_PERIOD", period,
                (reopened ? "Reopened" : "Closed") + " fiscal period " + period, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuth(AuthAuditEvent e) {
        save(new AuditLog(e.success() ? "LOGIN_SUCCESS" : "LOGIN_FAILURE", e.username(), null, null,
                (e.success() ? "Login success" : "Login failure") + " for " + e.username(), null));
    }

    /**
     * Records every tool ERP Copilot actually executed (reads, and writes the user confirmed). Plain
     * {@code @EventListener}: the assistant publishes this from its (non-transactional) streaming thread
     * after the tool's own HTTP call has already committed, so there is no ambient transaction to hook —
     * {@link AuditWriter} opens its own. Only truncated summaries are stored, never full payloads.
     */
    @EventListener
    public void onAssistantToolExecuted(AssistantToolExecutedEvent e) {
        String status = e.ok() ? "ok" : "failed";
        save(new AuditLog("ASSISTANT_TOOL_EXECUTED", e.actor(), "ASSISTANT_TOOL", e.toolName(),
                "ERP Copilot executed " + e.kind() + " tool " + e.toolName() + " (" + status + ")",
                "input=" + e.inputSummary() + "; result=" + e.resultSummary()));
    }

    private void save(AuditLog entry) {
        try {
            writer.write(entry);
        } catch (RuntimeException ex) {
            log.error("audit write failed for event {}", entry.getEventType(), ex);
        }
    }
}
