package com.erp.ledger.domain;

/**
 * Posting gate for a fiscal period. OPEN accepts postings; CLOSED blocks new postings (soft close);
 * LOCKED blocks even reversals (hard close — reserved for a later phase).
 */
public enum FiscalPeriodStatus {
    OPEN,
    CLOSED,
    LOCKED
}
