package com.erp.iam.api;

/**
 * Application roles, mirroring the write-authorization matrix in {@code SecurityConfig}. The guest
 * account intentionally has <em>no</em> role (an empty role set): it is authenticated and may read, but
 * holds no write role, so every {@code hasRole(...)} POST returns 403.
 */
public enum Role {
    ADMIN,
    ACCOUNTANT,
    WAREHOUSE,
    SALES
}
