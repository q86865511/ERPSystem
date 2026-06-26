package com.erp.payments.domain;

/** Direction of a payment: OUT pays a vendor (Phase 2), IN receives from a customer (Phase 3). */
public enum PaymentDirection {
    IN,
    OUT
}
