package com.erp.assistant.application;

/**
 * Whether a tool only reads ERP data or mutates it. The distinction is load-bearing: {@code WRITE} tools
 * are gated behind human-in-the-loop confirmation and are not offered to role-less (guest) callers. The
 * kind is decided by the backend manifest, never by anything the model or the client sends.
 */
public enum ToolKind {
    READ,
    WRITE;

    /** Parses the manifest's {@code "read"}/{@code "write"} (case-insensitive). */
    static ToolKind fromManifest(String value) {
        if (value == null) {
            throw new IllegalArgumentException("tool kind must not be null");
        }
        return switch (value.trim().toLowerCase()) {
            case "read" -> READ;
            case "write" -> WRITE;
            default -> throw new IllegalArgumentException("unknown tool kind: " + value);
        };
    }
}
