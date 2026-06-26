package com.erp.sales.domain;

/** Lifecycle of a sales order. An SO is a commitment and posts nothing; deliveries drive its status. */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_SHIPPED,
    SHIPPED,
    CLOSED
}
