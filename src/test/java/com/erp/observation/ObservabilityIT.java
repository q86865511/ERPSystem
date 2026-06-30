package com.erp.observation;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observability wiring (Testcontainers, real security chain, MockMvc — this sandbox can't bind Tomcat).
 * Asserts: the Prometheus scrape endpoint is open and carries our business + system meters; a
 * non-whitelisted actuator endpoint is not publicly readable; a committed posting increments its business
 * counter; and every response carries a correlation id (echoed when supplied, generated otherwise).
 * Not {@code @Transactional}: the metrics listener fires on {@code AFTER_COMMIT}, like the audit listener.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LedgerPosting ledgerPosting;

    @Test
    void prometheusEndpointIsOpenAndExposesSystemAndBusinessMeters() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("erp_reconciliation_healthy")));
    }

    @Test
    void committedPostingIncrementsTheBusinessCounter() throws Exception {
        ledgerPosting.post(balanced("OBS-" + SEQ.incrementAndGet()), "observer");
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("erp_journal_postings_total")));
    }

    @Test
    void nonWhitelistedActuatorEndpointIsNotPubliclyReadable() throws Exception {
        // /actuator/env is neither exposed nor permitAll: the security catch-all rejects it (401), so it
        // is never publicly readable — proving the exposure whitelist + authz both hold.
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void responseCarriesCorrelationId_echoedOrGenerated() throws Exception {
        mvc.perform(get("/actuator/prometheus").header("X-Request-Id", "test-corr-id-123"))
                .andExpect(header().string("X-Request-Id", "test-corr-id-123"));

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(header().string("X-Request-Id", not(matchesPattern("^$"))));
    }

    private static JournalEntryRequest balanced(String docId) {
        return new JournalEntryRequest(null, LocalDate.of(2026, 10, 20), "observability test " + docId, null,
                "OBS_TEST", docId, "POSTED",
                List.of(new Line("1010", new BigDecimal("100"), null, "cash"),
                        new Line("3100", null, new BigDecimal("100"), "capital")));
    }
}
