package com.candescent.examples.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mocked pattern tests for Java examples (mirrors examples/typescript/test/examples.test.ts).
 */
class ExamplesTest {
    private record ExampleCase(String name, String mainClass, Map<String, String> env) {}

    private static ExampleMockServer mockServer;

    @BeforeAll
    static void startMockServer() throws Exception {
        mockServer = new ExampleMockServer();
    }

    @AfterAll
    static void stopMockServer() throws Exception {
        if (mockServer != null) {
            mockServer.close();
            mockServer = null;
        }
    }

    static Stream<ExampleCase> mockedExamples() {
        return Stream.of(
                example("accounts", "com.candescent.examples.AccountsExample", MockEnv.RETAIL_USER_ENV),
                example(
                        "transactions",
                        "com.candescent.examples.TransactionsExample",
                        MockEnv.RETAIL_USER_ENV),
                example(
                        "authentication",
                        "com.candescent.examples.AuthenticationExample",
                        MockEnv.merge(
                                MockEnv.RETAIL_USER_ENV,
                                Map.of(
                                        "CANDESCENT_OIDC_CLIENT_ID",
                                        "test-oidc-client-id",
                                        "CANDESCENT_INSTITUTION_USER_ID",
                                        "inst-user-1"))),
                example(
                        "authentication-lifecycle",
                        "com.candescent.examples.AuthenticationLifecycleExample",
                        MockEnv.RETAIL_USER_ENV),
                example(
                        "customer-management",
                        "com.candescent.examples.CustomerManagementExample",
                        MockEnv.merge(
                                MockEnv.BASE_CLIENT_ENV,
                                Map.of("CANDESCENT_CUSTOMER_ID", "cust-001"))),
                example(
                        "register-and-lookup",
                        "com.candescent.examples.RegisterAndLookupExample",
                        MockEnv.merge(
                                MockEnv.BASE_CLIENT_ENV,
                                Map.of("FALLBACK_CUSTOMER_ID", "cust-guid-001"))),
                example(
                        "disclosures",
                        "com.candescent.examples.DisclosuresExample",
                        MockEnv.RETAIL_USER_ENV),
                example(
                        "estatements",
                        "com.candescent.examples.EstatementsExample",
                        MockEnv.merge(
                                MockEnv.RETAIL_USER_ENV,
                                Map.of("CANDESCENT_CUSTOMER_ID", "cust-001"))),
                example(
                        "alert-configuration",
                        "com.candescent.examples.AlertConfigurationExample",
                        MockEnv.BASE_CLIENT_ENV),
                example(
                        "alert-preferences",
                        "com.candescent.examples.AlertPreferencesExample",
                        MockEnv.RETAIL_USER_ENV),
                example(
                        "alert-delivery",
                        "com.candescent.examples.AlertDeliveryExample",
                        MockEnv.RETAIL_USER_ENV),
                example(
                        "notification-channels",
                        "com.candescent.examples.NotificationChannelsExample",
                        MockEnv.merge(
                                MockEnv.BASE_CLIENT_ENV,
                                Map.of("CANDESCENT_HOST_USER_ID", "HOST01"))),
                example(
                        "business-registration",
                        "com.candescent.examples.BusinessRegistrationExample",
                        MockEnv.BUSINESS_ENV),
                example(
                        "business-entitlements",
                        "com.candescent.examples.BusinessEntitlementsExample",
                        MockEnv.BUSINESS_ENV),
                example(
                        "business-payments",
                        "com.candescent.examples.BusinessPaymentsExample",
                        MockEnv.merge(
                                MockEnv.BUSINESS_ENV,
                                Map.of(
                                        "CANDESCENT_LOGIN_ID",
                                        "login01",
                                        "CANDESCENT_ACCOUNT_ID",
                                        "acct-001"))),
                example(
                        "customer-campaigns",
                        "com.candescent.examples.CustomerCampaignsExample",
                        MockEnv.BASE_CLIENT_ENV),
                example(
                        "money-movement",
                        "com.candescent.examples.MoneyMovementExample",
                        MockEnv.merge(
                                MockEnv.RETAIL_USER_ENV,
                                Map.of(
                                        "CANDESCENT_ACCOUNT_ID",
                                        "acct-001",
                                        "FALLBACK_RECIPIENT_ID",
                                        "rcp-001"))),
                example(
                        "mx-service",
                        "com.candescent.examples.MxServiceExample",
                        MockEnv.merge(
                                MockEnv.BASE_CLIENT_ENV,
                                Map.of(
                                        "CANDESCENT_MX_USER_ID",
                                        "mx-user-1",
                                        "CANDESCENT_MX_CLIENT_ID",
                                        "test-mx-client-id",
                                        "CANDESCENT_MX_CLIENT_SECRET",
                                        "test-mx-client-secret",
                                        "CANDESCENT_MX_INSTITUTION_ID",
                                        "05523"))),
                example(
                        "pagination",
                        "com.candescent.examples.PaginationExample",
                        MockEnv.merge(
                                MockEnv.BASE_CLIENT_ENV,
                                Map.of("CANDESCENT_HOST_USER_ID", "HOST01"))),
                example(
                        "error-handling",
                        "com.candescent.examples.ErrorHandlingExample",
                        MockEnv.RETAIL_USER_ENV),
                example(
                        "user-status",
                        "com.candescent.examples.UserStatusExample",
                        MockEnv.merge(
                                MockEnv.BASE_CLIENT_ENV,
                                Map.of("CANDESCENT_HOST_USER_ID", "HOST01"))));
    }

    private static ExampleCase example(String name, String mainClass, Map<String, String> env) {
        return new ExampleCase(name, mainClass, env);
    }

    @ParameterizedTest(name = "{0} completes with mocked HTTP (subprocess)")
    @MethodSource("mockedExamples")
    void mockedExampleCompletes(ExampleCase exampleCase) throws Exception {
        ExampleProcessRunner.RunResult result =
                ExampleProcessRunner.runExample(exampleCase.mainClass(), fullEnv(exampleCase.env()));
        assertEquals(
                0,
                result.exitCode(),
                () -> exampleCase.name() + " failed:\n" + result.stderr() + result.stdout());
    }

    @Test
    void businessEntitlementsExitsWhenBbCredentialsMissing() throws Exception {
        Map<String, String> env = fullEnv(MockEnv.merge(
                MockEnv.BASE_CLIENT_ENV,
                Map.of(
                        "CANDESCENT_BB_LOGIN_ID",
                        "bb-login",
                        "CANDESCENT_BB_BUSINESS_ID",
                        "bb-business")));
        ExampleProcessRunner.RunResult result =
                ExampleProcessRunner.runExample("com.candescent.examples.BusinessEntitlementsExample", env);
        assertNotEquals(0, result.exitCode());
        String combined = result.stderr() + result.stdout();
        assertTrue(
                combined.contains("CANDESCENT_BB_CLIENT_ID")
                        || combined.contains("CANDESCENT_BB_CLIENT_SECRET"),
                () -> "expected BB credential error in output: " + combined);
    }

    @Test
    void authenticationExitsWhenOidcClientIdMissing() throws Exception {
        ExampleProcessRunner.RunResult result = ExampleProcessRunner.runExample(
                "com.candescent.examples.AuthenticationExample", fullEnv(MockEnv.RETAIL_USER_ENV));
        assertNotEquals(0, result.exitCode());
        String combined = result.stderr() + result.stdout();
        assertTrue(
                combined.contains("CANDESCENT_OIDC_CLIENT_ID"),
                () -> "expected OIDC client id error in output: " + combined);
    }

    private static Map<String, String> fullEnv(Map<String, String> exampleEnv) {
        Map<String, String> env = new LinkedHashMap<>(mockServer.mockEnvOverrides());
        env.putAll(exampleEnv);
        return env;
    }
}
