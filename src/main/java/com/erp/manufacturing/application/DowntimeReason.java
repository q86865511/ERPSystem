package com.erp.manufacturing.application;

/** Total downtime minutes attributed to one reason, across all equipment. */
public record DowntimeReason(String reason, long minutes) {
}
