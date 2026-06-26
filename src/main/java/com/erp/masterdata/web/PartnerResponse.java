package com.erp.masterdata.web;

import com.erp.masterdata.domain.Partner;

/** API view of a business partner. */
public record PartnerResponse(
        Long id,
        String code,
        String name,
        boolean vendor,
        boolean customer,
        String taxId,
        int paymentTermsDays,
        String apAccountCode,
        String arAccountCode,
        boolean active) {

    public static PartnerResponse from(Partner partner) {
        return new PartnerResponse(
                partner.getId(),
                partner.getCode(),
                partner.getName(),
                partner.isVendor(),
                partner.isCustomer(),
                partner.getTaxId(),
                partner.getPaymentTermsDays(),
                partner.getApAccountCode(),
                partner.getArAccountCode(),
                partner.isActive());
    }
}
