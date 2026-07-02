package com.erp.purchasing.application;

import java.math.BigDecimal;

/** A vendor's delivery performance: how many received lines had an expected date, and how many arrived
 *  on time (goods-receipt posting date on or before the line's expected delivery date). */
public record SupplierPerformance(Long partnerId, String name, int totalReceipts, int onTime,
                                  BigDecimal onTimePct) {
}
