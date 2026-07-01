package com.erp.hr.domain;

import com.erp.hr.api.PayrollStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Unit tests for the {@link Payroll} DRAFT → POSTED state machine and total roll-up. */
class PayrollTest {

    private Payroll draftWithTwoLines() {
        Payroll p = new Payroll(2026, 6);
        p.addLine(1L, new BigDecimal("100.00"), new BigDecimal("6.00"), new BigDecimal("5.00"),
                new BigDecimal("89.00"));
        p.addLine(2L, new BigDecimal("200.00"), new BigDecimal("12.00"), new BigDecimal("10.00"),
                new BigDecimal("178.00"));
        return p;
    }

    @Test
    void newPayrollStartsDraftWithZeroTotals() {
        Payroll p = new Payroll(2026, 6);
        assertThat(p.getStatus()).isEqualTo(PayrollStatus.DRAFT);
        assertThat(p.getGrossTotal()).isEqualByComparingTo("0");
    }

    @Test
    void addLineRollsUpTotalsAndStaysBalanced() {
        Payroll p = draftWithTwoLines();
        assertThat(p.getLines()).hasSize(2);
        assertThat(p.getGrossTotal()).isEqualByComparingTo("300.00");
        assertThat(p.getTaxTotal()).isEqualByComparingTo("18.00");
        assertThat(p.getInsuranceTotal()).isEqualByComparingTo("15.00");
        assertThat(p.getNetTotal()).isEqualByComparingTo("267.00");
        // gross == tax + insurance + net
        assertThat(p.getTaxTotal().add(p.getInsuranceTotal()).add(p.getNetTotal()))
                .isEqualByComparingTo(p.getGrossTotal());
    }

    @Test
    void postMovesToPostedAndRecordsTheEntry() {
        Payroll p = draftWithTwoLines();
        p.markPosted(42L, LocalDate.of(2026, 7, 1));
        assertThat(p.getStatus()).isEqualTo(PayrollStatus.POSTED);
        assertThat(p.getJournalEntryId()).isEqualTo(42L);
        assertThat(p.getPostingDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void aPostedPayrollCannotBePostedAgain() {
        Payroll p = draftWithTwoLines();
        p.markPosted(42L, LocalDate.of(2026, 7, 1));
        assertThatIllegalStateException().isThrownBy(() -> p.markPosted(43L, LocalDate.of(2026, 7, 2)));
    }

    @Test
    void cannotAddLinesAfterPosting() {
        Payroll p = draftWithTwoLines();
        p.markPosted(42L, LocalDate.of(2026, 7, 1));
        assertThatIllegalStateException().isThrownBy(() ->
                p.addLine(3L, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("100.00")));
    }

    @Test
    void invalidMonthIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Payroll(2026, 13));
    }
}
