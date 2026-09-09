package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.ClientConfig;
import com.candescent.di.Environment;
import com.candescent.di.errors.NotFoundException;
import com.candescent.di.generated.ApiException;
import com.candescent.di.generated.model.EStatementPreferencesRequest;
import com.candescent.di.generated.model.EStatementReportRequest;
import com.candescent.di.generated.model.EStatementRequest;

/**
 * Electronic statements (mirrors examples/typescript/estatements.ts).
 *
 * <p>Account-level disclosure and preference steps use apigee-electronic-statements-stg
 * defaults (institution 05523) because institution 05529 does not have MultiStatement configured.
 */
public class EstatementsExample {
    private static final String STEP_HEADING_SUFFIX = ") ===";
    private static final String DEFAULT_ESTATEMENT_INSTITUTION_ID = "05523";
    private static final String DEFAULT_ESTATEMENT_HOST_USER_ID = "120230424";
    private static final String DEFAULT_ESTATEMENT_LOGIN_ID = "estatement01";
    private static final String DEFAULT_ESTATEMENT_ACCOUNT_NUMBER = "0003";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();

        String estatementHostUserId = ExampleHelpers.envOrDefault(
                "CANDESCENT_ESTATEMENT_HOST_USER_ID", DEFAULT_ESTATEMENT_HOST_USER_ID);
        String estatementLoginId = ExampleHelpers.envOrDefault(
                "CANDESCENT_ESTATEMENT_LOGIN_ID", DEFAULT_ESTATEMENT_LOGIN_ID);
        String estatementAccountNumber = ExampleHelpers.envOrDefault(
                "CANDESCENT_ESTATEMENT_ACCOUNT_NUMBER", DEFAULT_ESTATEMENT_ACCOUNT_NUMBER);
        String estatementAccountId = ExampleHelpers.envOrNull("CANDESCENT_ESTATEMENT_ACCOUNT_ID");

        String reportHostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String reportLoginId = ExampleHelpers.envOrNull("CANDESCENT_LOGIN_ID");

        try (CandescentClient reportClient = ExampleHelpers.clientFromEnv();
                CandescentClient estatementClient = createEstatementClient()) {
            ExampleConsole.println("Client initialised");
            ExampleConsole.println("  E-statement institution: "
                    + ExampleHelpers.envOrDefault(
                            "CANDESCENT_ESTATEMENT_INSTITUTION_ID", DEFAULT_ESTATEMENT_INSTITUTION_ID));

            String resolvedAccountId = ExampleHelpers.resolveAccountId(
                    estatementClient,
                    estatementHostUserId,
                    estatementLoginId,
                    estatementAccountId,
                    estatementAccountNumber);

            ExampleHelpers.runExampleStepWhen(
                    estatementHostUserId != null && !estatementHostUserId.isBlank(),
                    "\n  Skipping hostUserId path (CANDESCENT_ESTATEMENT_HOST_USER_ID not set)",
                    "",
                    () -> runEstatementFlow(
                            estatementClient,
                            "hostUserId",
                            resolvedAccountId,
                            estatementHostUserId,
                            null));

            ExampleHelpers.runExampleStepWhen(
                    estatementLoginId != null && !estatementLoginId.isBlank(),
                    "\n  Skipping loginId path (CANDESCENT_ESTATEMENT_LOGIN_ID not set)",
                    "",
                    () -> runEstatementFlow(
                            estatementClient,
                            "loginId",
                            resolvedAccountId,
                            null,
                            estatementLoginId));

            runEstatementsReport(reportClient, reportHostUserId, reportLoginId);
        }
        ExampleConsole.done();
    }

    private static CandescentClient createEstatementClient() {
        String clientId = System.getenv("CANDESCENT_CLIENT_ID");
        String clientSecret = System.getenv("CANDESCENT_CLIENT_SECRET");
        String institutionId = ExampleHelpers.envOrDefault(
                "CANDESCENT_ESTATEMENT_INSTITUTION_ID", DEFAULT_ESTATEMENT_INSTITUTION_ID);

        ClientConfig config = new ClientConfig()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setInstitutionId(institutionId);
        String baseUrl = System.getenv("CANDESCENT_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            config.setBaseUrl(baseUrl);
        } else {
            String envName = System.getenv("CANDESCENT_ENVIRONMENT");
            config.setEnvironment(Environment.fromEnvName(envName));
        }
        CandescentClient client = new CandescentClient(config);
        ExampleHelpers.configureClientParsing(client);
        return client;
    }

    private static void runEstatementFlow(
            CandescentClient client,
            String label,
            String accountId,
            String hostUserId,
            String loginId) {
        ExampleConsole.println("\n" + "=".repeat(72));
        ExampleConsole.println("  E-Statement Flow — " + label);
        ExampleConsole.println("=".repeat(72));

        ExampleHelpers.runExampleStep(
                "\n=== Get E-Statement Disclosures (" + label + STEP_HEADING_SUFFIX,
                () -> runEstatementStep(
                        "E-Statement Disclosures — " + label,
                        () -> {
                            var builder = client.electronicStatements().getDisclosuresByAccount(accountId);
                            if (hostUserId != null) {
                                builder.hostUserId(hostUserId);
                            } else {
                                builder.loginId(loginId);
                            }
                            return ExampleHelpers.execute(client, builder);
                        }));

        ExampleHelpers.runExampleStep(
                "\n=== Update Delivery Preference — opt-in (" + label + STEP_HEADING_SUFFIX,
                () -> runEstatementStep(
                        "Updated delivery preference — " + label,
                        () -> {
                            EStatementRequest request = new EStatementRequest();
                            request.setActivateEstatement(true);
                            request.setAccountId(accountId);
                            var builder = client.electronicStatements().updateStatementDeliveryPreference(request);
                            if (hostUserId != null) {
                                builder.hostUserId(hostUserId);
                            } else {
                                builder.loginId(loginId);
                            }
                            ExampleHelpers.execute(client, builder);
                            ExampleConsole.println("  Updated delivery preference successfully");
                            return null;
                        }));

        ExampleHelpers.runExampleStep(
                "\n=== Update E-Statement Preferences — all accounts (" + label + STEP_HEADING_SUFFIX,
                () -> {
                    EStatementPreferencesRequest prefs = new EStatementPreferencesRequest();
                    prefs.setActivateEstatement(true);
                    var builder = client.electronicStatements().updateEStatementPreferences(prefs);
                    if (hostUserId != null) {
                        builder.hostUserId(hostUserId);
                    } else {
                        builder.loginId(loginId);
                    }
                    ExampleHelpers.execute(client, builder);
                    ExampleConsole.println("  Updated e-statement preferences successfully");
                });
    }

    private static void runEstatementStep(String label, ThrowingSupplier<?> fn) throws Exception {
        try {
            Object result = fn.get();
            if (result != null) {
                ExampleHelpers.pretty(label, result);
            }
        } catch (ApiException ex) {
            if (isMultiStatementNotConfigured(ex)) {
                ExampleConsole.println(
                        "  Skipping — account-level e-statement APIs require MultiStatement"
                                + " configuration for this institution in staging");
                return;
            }
            throw CandescentClient.mapException(ex);
        }
    }

    private static boolean isMultiStatementNotConfigured(ApiException ex) {
        String body = ex.getResponseBody();
        return body != null && body.contains("UXESTMT_11021");
    }

    @FunctionalInterface
    @SuppressWarnings("java:S112")
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static void runEstatementsReport(
            CandescentClient client, String hostUserId, String loginId) {
        String institutionCustomerId = ExampleHelpers.envOrNull("CANDESCENT_CUSTOMER_ID");
        ExampleHelpers.runExampleStep("\n=== 4. Get E-Statements Report (opt-in data) ===", () -> {
            if (institutionCustomerId == null) {
                ExampleConsole.println(
                        "  Skipped: CANDESCENT_CUSTOMER_ID not set (institution customer ID required for report)");
                return;
            }
            EStatementReportRequest reportRequest = new EStatementReportRequest();
            reportRequest.setCustomerId(institutionCustomerId);
            reportRequest.setAccountType("SDA");
            try {
                var builder = client.electronicStatements().getEstatementsReport(reportRequest);
                if (hostUserId != null && !hostUserId.isBlank()) {
                    builder.hostUserId(hostUserId);
                } else if (loginId != null) {
                    builder.loginId(loginId);
                }
                ExampleHelpers.pretty("E-Statements Report", ExampleHelpers.execute(client, builder));
            } catch (NotFoundException e) {
                ExampleConsole.println(
                        "  Skipped: no entitled customers found for this institution customer ID in staging");
            }
        });
    }
}
