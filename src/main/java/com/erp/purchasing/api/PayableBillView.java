package com.erp.purchasing.api;

import java.math.BigDecimal;

/** Published view of a payable vendor bill — what the payments module needs to allocate against it. */
public record PayableBillView(
        Long id,
        String billNumber,
        Long partnerId,
        BigDecimal grossAmount,
        BigDecimal amountPaid,
        BigDecimal openBalance,
        String status) {
}
