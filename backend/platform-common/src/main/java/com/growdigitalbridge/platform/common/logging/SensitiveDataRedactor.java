package com.growdigitalbridge.platform.common.logging;

import java.util.Set;

/** Redacts header values that must never enter request logs. */
public final class SensitiveDataRedactor {
    private static final Set<String> SENSITIVE = Set.of("authorization", "cookie", "set-cookie", "x-api-key");
    private SensitiveDataRedactor() { }
    public static String headerValue(String name, String value) {
        return SENSITIVE.contains(name.toLowerCase()) ? "[REDACTED]" : value;
    }
}
