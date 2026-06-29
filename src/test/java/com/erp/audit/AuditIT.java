package com.erp.audit;

import com.erp.TestcontainersConfiguration;
import com.erp.audit.application.AuditLogRepository;
import com.erp.audit.domain.AuditLog;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail (Testcontainers, real DB, real security chain). Deliberately <em>not</em>
 * {@code @Transactional}: the listener fires on {@code AFTER_COMMIT}, so the triggering action must really
 * commit — a test transaction rolled back at the end would suppress every audit row. Asserts that committed
 * actions (journal post, period close/reopen, login) are recorded, rolled-back work is not, the table is
 * append-only, and the viewer is ADMIN-only. Uses 2026 periods 10/11 (untouched by other tests).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditIT {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LedgerPosting ledgerPosting;
    @Autowired
    private AuditLogRepository auditLog;

    @Test
    void postingAJournalEntryIsAudited() {
        String docId = "AUDIT-JE-" + SEQ.incrementAndGet();
        PostingResult result = ledgerPosting.post(balanced(docId), "auditor");

        List<AuditLog> rows = auditLog.findByRefTypeAndRefId("JOURNAL_ENTRY", String.valueOf(result.entryNo()));
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo("JOURNAL_POSTED");
        assertThat(row.getActor()).isEqualTo("auditor");
        assertThat(row.getSummary()).contains(String.valueOf(result.entryNo()));
    }

    @Test
    void rolledBackPostingLeavesNoAuditRow() {
        long before = auditLog.countByEventType("JOURNAL_POSTED");
        assertThatThrownBy(() -> ledgerPosting.post(unbalanced("AUDIT-BAD-" + SEQ.incrementAndGet()), "auditor"))
                .isInstanceOf(RuntimeException.class);
        assertThat(auditLog.countByEventType("JOURNAL_POSTED")).isEqualTo(before);
    }

    @Test
    void closingAndReopeningAPeriodIsAudited() throws Exception {
        String admin = login("admin", "admin");
        mvc.perform(post("/api/ledger/fiscal-years/2026/periods/11/close").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ledger/fiscal-years/2026/periods/11/reopen").header("Authorization", admin))
                .andExpect(status().isOk());

        List<String> types = auditLog.findByRefTypeAndRefId("FISCAL_PERIOD", "2026/11")
                .stream().map(AuditLog::getEventType).toList();
        assertThat(types).contains("PERIOD_CLOSED", "PERIOD_REOPENED");
    }

    @Test
    void loginSuccessAndFailureAreAudited() throws Exception {
        login("admin", "admin"); // success → LOGIN_SUCCESS
        long failBefore = auditLog.countByEventType("LOGIN_FAILURE");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(auditLog.countByEventType("LOGIN_FAILURE")).isGreaterThan(failBefore);
        assertThat(auditLog.findAll().stream()
                .anyMatch(a -> "LOGIN_SUCCESS".equals(a.getEventType()) && "admin".equals(a.getActor())))
                .isTrue();
    }

    @Test
    void auditViewerIsAdminOnly() throws Exception {
        mvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/audit").header("Authorization", login("guest", "guest")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", login("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void auditLogIsAppendOnly() {
        Long id = auditLog.save(new AuditLog("TEST_MARKER", "tester", null, null, "append-only probe", null))
                .getId();
        assertThatThrownBy(() -> {
            auditLog.deleteById(id);
            auditLog.flush();
        }).isInstanceOf(Exception.class);
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = ACCESS_TOKEN.matcher(body);
        if (!m.find()) {
            throw new IllegalStateException("no accessToken in login response: " + body);
        }
        return "Bearer " + m.group(1);
    }

    private static JournalEntryRequest balanced(String docId) {
        return new JournalEntryRequest(null, LocalDate.of(2026, 10, 15), "audit test " + docId, null,
                "AUDIT_TEST", docId, "POSTED",
                List.of(new Line("1010", new BigDecimal("100"), null, "cash"),
                        new Line("3100", null, new BigDecimal("100"), "capital")));
    }

    private static JournalEntryRequest unbalanced(String docId) {
        return new JournalEntryRequest(null, LocalDate.of(2026, 10, 15), "audit bad " + docId, null,
                "AUDIT_TEST", docId, "POSTED",
                List.of(new Line("1010", new BigDecimal("100"), null, "cash"),
                        new Line("3100", new BigDecimal("50"), null, "wrong")));
    }
}
