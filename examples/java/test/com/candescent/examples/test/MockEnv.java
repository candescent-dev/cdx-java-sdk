package com.candescent.examples.test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test environment presets for Java example runs (mirrors examples/typescript/test/mock-env.ts).
 */
final class MockEnv {
    private MockEnv() {}

    /** Base credentials for {@link com.candescent.di.CandescentClient#fromEnv()}. */
    static final Map<String, String> BASE_CLIENT_ENV = Map.of(
            "CANDESCENT_CLIENT_ID", "test-client-id",
            "CANDESCENT_CLIENT_SECRET", "test-client-secret",
            "CANDESCENT_INSTITUTION_ID", "05523");

    /** Common retail user context. */
    static final Map<String, String> RETAIL_USER_ENV = merge(
            BASE_CLIENT_ENV,
            Map.of(
                    "CANDESCENT_HOST_USER_ID", "HOST01",
                    "CANDESCENT_LOGIN_ID", "login01",
                    "CANDESCENT_ACCOUNT_ID", "acct-001"));

    /** Business banking context (BB Apigee credentials + identifiers). */
    static final Map<String, String> BUSINESS_ENV = merge(
            BASE_CLIENT_ENV,
            Map.of(
                    "CANDESCENT_BB_CLIENT_ID", "test-bb-client-id",
                    "CANDESCENT_BB_CLIENT_SECRET", "test-bb-client-secret",
                    "CANDESCENT_BB_LOGIN_ID", "bb-login",
                    "CANDESCENT_BB_BUSINESS_ID", "bb-business-1",
                    "CANDESCENT_BB_INSTITUTION_CUSTOMER_ID", "bb-location-1",
                    "CANDESCENT_BB_ACCOUNT_ID", "acct-001"));

    static Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(extra);
        return Map.copyOf(merged);
    }
}
