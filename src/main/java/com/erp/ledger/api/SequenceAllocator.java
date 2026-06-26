package com.erp.ledger.api;

/**
 * Published port over the shared number-sequence kernel (the {@code number_sequence} table the ledger
 * owns as module zero). Other modules allocate human-readable, unique-monotonic business document
 * numbers (e.g. {@code ADJ-000001}) through this, in the caller's transaction.
 */
public interface SequenceAllocator {

    /**
     * Takes the next value for a scope under a pessimistic row lock and returns it formatted with the
     * scope's prefix and zero-padding. Must be called within a transaction.
     */
    String next(String scope);
}
