package com.erp.masterdata.api;

/**
 * Which side of a movement's journal entry a posting rule supplies: the INVENTORY control account
 * (by item type) or the COUNTER account (by movement type, e.g. the adjustment loss/gain account).
 */
public enum PostingRuleRole {
    INVENTORY,
    COUNTER
}
