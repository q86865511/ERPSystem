package com.erp.masterdata.api;

/**
 * How an item is valued. MVP uses MOVING_AVERAGE everywhere; STANDARD (with purchase-price and
 * manufacturing variances) is a reserved, additive v2 enhancement — the column exists now so adding
 * it later is non-breaking.
 */
public enum ValuationMethod {
    MOVING_AVERAGE,
    STANDARD
}
