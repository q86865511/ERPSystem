package com.erp.masterdata.api;

/**
 * Published read view of a business partner — what purchasing/sales need without importing the
 * {@code Partner} entity: identity, role flags, payment terms, and optional control-account overrides.
 */
public record PartnerView(
        Long id,
        String code,
        String name,
        boolean vendor,
        boolean customer,
        String taxId,
        int paymentTermsDays,
        String apAccountCode,
        String arAccountCode) {
}
