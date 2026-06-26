package com.erp.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * A business partner — a vendor and/or a customer (the role flags are not exclusive). Carries the
 * payment terms used for AR/AP aging and optional account-code overrides for its control accounts
 * (defaulting to the standard 2100/1200 when null).
 */
@Entity
@Table(name = "partner")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_vendor", nullable = false)
    private boolean vendor;

    @Column(name = "is_customer", nullable = false)
    private boolean customer;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "ap_account_code")
    private String apAccountCode;

    @Column(name = "ar_account_code")
    private String arAccountCode;

    @Column(nullable = false)
    private boolean active;

    public Partner(String code, String name, boolean vendor, boolean customer, String taxId,
                   int paymentTermsDays, String apAccountCode, String arAccountCode) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (paymentTermsDays < 0) {
            throw new IllegalArgumentException("paymentTermsDays must not be negative");
        }
        this.code = code;
        this.name = name;
        this.vendor = vendor;
        this.customer = customer;
        this.taxId = taxId;
        this.paymentTermsDays = paymentTermsDays;
        this.apAccountCode = apAccountCode;
        this.arAccountCode = arAccountCode;
        this.active = true;
    }
}
