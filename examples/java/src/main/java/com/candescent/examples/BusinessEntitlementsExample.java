package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.ClientConfig;
import com.candescent.di.auth.ClientCredentialsProvider;
import com.candescent.di.auth.TokenEndpoint;
import com.candescent.di.auth.TokenProvider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Business Banking entitlements (mirrors examples/typescript/business-entitlements.ts).
 */
public class BusinessEntitlementsExample {
    private static final Logger LOG = Logger.getLogger(BusinessEntitlementsExample.class.getName());
    private static final String BB_ENTITLEMENTS_API_PREFIX = "/db-bb-entitlements";

    public static void main(String[] args) throws Exception {
        String bbLoginId = ExampleHelpers.envOrDefault("CANDESCENT_BB_LOGIN_ID", "demo-bb-login");
        String bbBusinessId = ExampleHelpers.envOrDefault("CANDESCENT_BB_BUSINESS_ID", "demo-business-id");
        String institutionCustomerId = ExampleHelpers.envOrNull("CANDESCENT_BB_INSTITUTION_CUSTOMER_ID");
        String featureName = ExampleHelpers.envOrNull("CANDESCENT_BB_FEATURE_NAME");

        final CandescentClient client;
        try {
            client = createBbClient();
        } catch (IllegalStateException e) {
            LOG.log(Level.SEVERE, e.getMessage());
            System.exit(1);
            return;
        }

        try (client) {
            ExampleConsole.println("Client initialised (Business Banking)");
            ExampleConsole.println("  Login ID:    " + ExampleHelpers.envSetStatus(bbLoginId));
            ExampleConsole.println("  Business ID: " + ExampleHelpers.envSetStatus(bbBusinessId));

            ExampleHelpers.runExampleStep("\n=== 1. User Entitlements — by loginId ===", () ->
                    ExampleHelpers.pretty(
                            "User Entitlements (loginId)",
                            ExampleHelpers.execute(client, client.entitlements().getUserEntitlements().loginId(bbLoginId))));

            ExampleHelpers.runExampleStepWhen(
                    institutionCustomerId != null,
                    "\n  Skipping location filter (CANDESCENT_BB_INSTITUTION_CUSTOMER_ID not set)",
                    "\n=== 2. User Entitlements — loginId + institutionCustomerId ===",
                    () -> ExampleHelpers.pretty(
                            "User Entitlements (loginId + location)",
                            ExampleHelpers.execute(client, client.entitlements()
                                    .getUserEntitlements()
                                    .loginId(bbLoginId)
                                    .institutionCustomerId(institutionCustomerId))));

            ExampleHelpers.runExampleStepWhen(
                    featureName != null,
                    "\n  Skipping feature filter (CANDESCENT_BB_FEATURE_NAME not set)",
                    "\n=== 3. User Entitlements — loginId + featureName ===",
                    () -> ExampleHelpers.pretty(
                            "User Entitlements (loginId + feature=" + featureName + ")",
                            ExampleHelpers.execute(client, client.entitlements()
                                    .getUserEntitlements()
                                    .loginId(bbLoginId)
                                    .featureName(featureName))));

            ExampleHelpers.runExampleStep("\n=== 4. Business Entitlements — by businessId ===", () ->
                    ExampleHelpers.pretty(
                            "Business Entitlements (businessId)",
                            ExampleHelpers.execute(client, client.entitlements().getBusinessEntitlements(bbBusinessId))));

            if (institutionCustomerId != null) {
                ExampleHelpers.runExampleStep(
                        "\n=== 5. Business Entitlements — businessId + institutionCustomerId ===",
                        () -> ExampleHelpers.pretty(
                                "Business Entitlements (businessId + location)",
                                ExampleHelpers.execute(client, client.entitlements()
                                        .getBusinessEntitlements(bbBusinessId)
                                        .institutionCustomerId(institutionCustomerId))));
            }

            if (featureName != null) {
                ExampleHelpers.runExampleStep(
                        "\n=== 6. Business Entitlements — businessId + featureName ===",
                        () -> ExampleHelpers.pretty(
                                "Business Entitlements (businessId + feature=" + featureName + ")",
                                ExampleHelpers.execute(client, client.entitlements()
                                        .getBusinessEntitlements(bbBusinessId)
                                        .featureName(featureName))));
            }

            ExampleHelpers.runExpectedErrorStep(
                    "\n=== 7. Negative: User entitlements without loginId (expect error) ===",
                    () -> ExampleHelpers.execute(client, client.entitlements().getUserEntitlements().loginId("")));

            ExampleHelpers.runExpectedErrorStep(
                    "\n=== 8. Negative: Business entitlements without businessId (expect error) ===",
                    () -> ExampleHelpers.execute(client, client.entitlements().getBusinessEntitlements("")));
        } catch (Exception e) {
            ExampleConsole.println("  Failed: " + e);
        }
        ExampleConsole.done();
    }

    private static CandescentClient createBbClient() {
        String clientId = System.getenv("CANDESCENT_BB_CLIENT_ID");
        String clientSecret = System.getenv("CANDESCENT_BB_CLIENT_SECRET");
        String institutionId = ExampleHelpers.firstNonBlank(
                System.getenv("CANDESCENT_BB_INSTITUTION_ID"),
                System.getenv("CANDESCENT_INSTITUTION_ID"));

        if (institutionId == null) {
            throw new IllegalStateException(
                    "Set CANDESCENT_BB_INSTITUTION_ID or CANDESCENT_INSTITUTION_ID");
        }
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException(
                    "Set CANDESCENT_BB_CLIENT_ID and CANDESCENT_BB_CLIENT_SECRET");
        }

        String apigeeBase = resolveApigeeBase();
        TokenProvider tokenProvider = new ClientCredentialsProvider(
                clientId, clientSecret, institutionId, apigeeBase, TokenEndpoint.V2_CURRENT);

        CandescentClient client = new CandescentClient(new ClientConfig()
                .setInstitutionId(institutionId)
                .setTokenProvider(tokenProvider)
                .setBaseUrl(apigeeBase + BB_ENTITLEMENTS_API_PREFIX));
        ExampleHelpers.configureClientParsing(client);
        return client;
    }

    private static String resolveApigeeBase() {
        String fromEnv = System.getenv("CANDESCENT_APIGEE_BASE_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return ExampleHelpers.assertCandescentHttpsBase(fromEnv);
        }
        String env = System.getenv("CANDESCENT_ENVIRONMENT");
        String candidate = "production".equalsIgnoreCase(env)
                ? "https://api.candescent.com/digitalbanking"
                : "https://api.candescent.com/digitalbanking/stage";
        return ExampleHelpers.assertCandescentHttpsBase(candidate);
    }
}
