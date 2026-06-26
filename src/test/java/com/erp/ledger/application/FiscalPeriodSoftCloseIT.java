package com.erp.ledger.application;

import com.erp.TestcontainersConfiguration;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration test for fiscal-period soft-close. Closing a period blocks new postings dated in it;
 * reopening restores them. Uses period 3 (March), which no other test posts into, to avoid interfering
 * with the shared container.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FiscalPeriodSoftCloseIT {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 15);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private FiscalPeriodService fiscalPeriodService;
    @Autowired
    private LedgerPosting ledgerPosting;

    @Test
    void closingAPeriodBlocksPostingUntilReopened() {
        fiscalPeriodService.close("2026", 3);
        assertThat(fiscalPeriodService.getPeriod("2026", 3).isOpen()).isFalse();

        assertThatThrownBy(() -> post("CLOSED-" + SEQ.incrementAndGet()))
                .isInstanceOf(PeriodNotOpenException.class);

        fiscalPeriodService.reopen("2026", 3);
        assertThat(fiscalPeriodService.getPeriod("2026", 3).isOpen()).isTrue();

        assertThatCode(() -> post("REOPENED-" + SEQ.incrementAndGet())).doesNotThrowAnyException();
    }

    private void post(String docId) {
        JournalEntryRequest request = new JournalEntryRequest(null, MARCH, "soft-close test " + docId,
                null, "SOFT_CLOSE_TEST", docId, "POSTED",
                List.of(new Line("1010", new BigDecimal("100"), null, "cash"),
                        new Line("3100", null, new BigDecimal("100"), "capital")));
        ledgerPosting.post(request, "tester");
    }
}
