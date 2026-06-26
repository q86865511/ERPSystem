package com.erp.reporting.application;

import java.math.BigDecimal;

/** One line of a financial statement: an account and its natural-sign balance. */
public record StatementLine(String code, String name, BigDecimal amount) {
}
