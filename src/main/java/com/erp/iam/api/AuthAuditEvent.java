package com.erp.iam.api;

/** Published on a login attempt (success or failure) for the security audit trail. */
public record AuthAuditEvent(String username, boolean success) {}
