package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.ClientConfig;
import com.candescent.di.Environment;
import com.candescent.di.errors.AuthenticationException;
import com.candescent.di.generated.JSON;
import com.candescent.di.generated.ApiException;
import com.google.gson.JsonObject;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MX Service example: list MX users via Platform proxy, get a user via Real Time
 * proxy, retrieve a widget URL via SSO proxy, and download transaction logs via
 * Reporting proxy.
 *
 * <p>Prerequisites (MX-only — does not use main {@code CANDESCENT_CLIENT_*} vars):
 * {@code CANDESCENT_MX_CLIENT_ID}, {@code CANDESCENT_MX_CLIENT_SECRET},
 * {@code CANDESCENT_MX_INSTITUTION_ID}.
 *
 * <p>OpenAPI spec: v1.8.0
 */
public class MxServiceExample {
    private static final String MX_API_HOST =
            ExampleHelpers.envOrDefault("CANDESCENT_MX_HOST", "api.mx.com");
    private static final String MX_LIVE_HOST =
            ExampleHelpers.envOrDefault("CANDESCENT_MX_LIVE_HOST", "live.moneydesktop.com");
    private static final String MX_SSO_HOST = "sso.moneydesktop.com";
    private static final String MX_PLATFORM_ACCEPT = "application/vnd.mx.api.v1+json";
    private static final String MX_SSO_ACCEPT_JSON = "application/vnd.moneydesktop.sso.v3+json";
    private static final String MX_SSO_CONTENT_TYPE_JSON = "application/vnd.moneydesktop.sso.v3+json";
    private static final String MX_LOGS_ACCEPT = "application/vnd.mx.logs.v1+avro";

    public static void main(String[] args) throws Exception {
        String institutionId = System.getenv("CANDESCENT_MX_INSTITUTION_ID");
        String mxUserId = System.getenv("CANDESCENT_MX_USER_ID");

        try (CandescentClient client = createMxClient()) {
            ExampleConsole.println("Client initialised");
            ExampleConsole.println("  MX API host:  " + MX_API_HOST);
            ExampleConsole.println("  MX Live host: " + MX_LIVE_HOST);
            ExampleConsole.println("  MX SSO host:  " + MX_SSO_HOST);
            ExampleConsole.println("  MX Logs host: " + resolveMxLogsHost());
            ExampleConsole.println("  Institution:  " + institutionId);

            String resolvedUserId = listMxUsers(client, mxUserId);
            ExampleHelpers.runExampleStepWhen(
                    resolvedUserId != null && !resolvedUserId.isBlank(),
                    "\n  Skipping get user / widget URL (no MX user_id available)",
                    "",
                    () -> runMxUserSteps(client, institutionId, resolvedUserId));
            downloadMxLogs(client, institutionId);
        }
        ExampleConsole.done();
    }

    /** MX Apigee credentials — isolated from main DI examples. */
    private static CandescentClient createMxClient() {
        String clientId = System.getenv("CANDESCENT_MX_CLIENT_ID");
        String clientSecret = System.getenv("CANDESCENT_MX_CLIENT_SECRET");
        String institutionId = System.getenv("CANDESCENT_MX_INSTITUTION_ID");

        if (institutionId == null || institutionId.isBlank()) {
            throw new IllegalStateException(
                    "Set CANDESCENT_MX_INSTITUTION_ID (MX institution, e.g. from apigee-mx-stg)");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "Set CANDESCENT_MX_CLIENT_ID and CANDESCENT_MX_CLIENT_SECRET (MX Apigee credentials)");
        }

        ClientConfig config = new ClientConfig()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setInstitutionId(institutionId);
        String baseUrl = System.getenv("CANDESCENT_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            config.setBaseUrl(baseUrl);
        } else {
            config.setEnvironment(resolveEnvironment());
        }
        CandescentClient client = new CandescentClient(config);
        ExampleHelpers.configureClientParsing(client);
        return client;
    }

    private static Environment resolveEnvironment() {
        return "production".equalsIgnoreCase(System.getenv("CANDESCENT_ENVIRONMENT"))
                ? Environment.PRODUCTION
                : Environment.STAGE;
    }

    private static String resolveMxLogsHost() {
        String fromEnv = System.getenv("CANDESCENT_MX_LOGS_HOST");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return resolveEnvironment() == Environment.PRODUCTION
                ? "logs.moneydesktop.com"
                : "int-logs.moneydesktop.com";
    }

    private static String listMxUsers(CandescentClient client, String existingUserId) {
        final String[] discovered = {existingUserId};
        ExampleHelpers.runExampleStep("\n=== 1. List MX Users (Platform proxy) ===", () -> {
            Map<String, Object> users = ExampleHelpers.execute(client, client.mxPlatform()
                    .getMxPlatformProxy(MX_API_HOST, MX_PLATFORM_ACCEPT, "users"));
            ExampleHelpers.pretty("MX Users", users);
            if (discovered[0] == null && users != null) {
                discovered[0] = discoverFirstMxUserId(users.get("users"));
            }
        });
        return discovered[0];
    }

    private static String discoverFirstMxUserId(Object usersList) {
        if (!(usersList instanceof Iterable<?> iterable)) {
            return null;
        }
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> user) {
                Object id = user.get("id");
                if (id == null) {
                    id = user.get("guid");
                }
                if (id != null) {
                    String discovered = id.toString();
                    ExampleConsole.println("\n  Auto-discovered MX user: " + discovered);
                    return discovered;
                }
            }
        }
        return null;
    }

    private static void runMxUserSteps(
            CandescentClient client, String institutionId, String userId) {
        ExampleHelpers.runExampleStep("\n=== 2. Get MX User (Real Time proxy) ===", () ->
                ExampleHelpers.pretty(
                        "MX User (" + userId + ")",
                        ExampleHelpers.execute(client, client.realTime()
                                .getMxRealTimeProxy(
                                        MX_LIVE_HOST,
                                        MX_SSO_ACCEPT_JSON,
                                        institutionId,
                                        "users/" + userId))));

        ExampleHelpers.runExampleStep("\n=== 3. Create MX Widget URL (SSO proxy) ===", () -> {
            try {
                JsonObject body = JSON.getGson().toJsonTree(connectWidgetRequestBody()).getAsJsonObject();
                Map<String, String> headers = new HashMap<>();
                headers.put("ext_host", MX_SSO_HOST);
                headers.put("Accept", MX_SSO_ACCEPT_JSON);
                headers.put("Content-Type", MX_SSO_CONTENT_TYPE_JSON);
                ExampleHelpers.pretty(
                        "MX Widget URL",
                        ExampleHelpers.postMxProxy(
                                client, institutionId, "users/" + userId + "/urls", body, headers));
            } catch (ApiException ex) {
                if (ex.getCode() == 404) {
                    ExampleConsole.println(
                            "  Skipping — MX widget URL not available for this user in staging");
                    return;
                }
                throw CandescentClient.mapException(ex);
            }
        });
    }

    private static Map<String, Object> connectWidgetRequestBody() {
        return Map.of(
                "url",
                Map.of(
                        "is_mobile_webview", false,
                        "style", Map.of("font_name", "Roboto"),
                        "type", "connect_widget",
                        "use_cases", List.of("PFM"),
                        "ui_message_version", 4));
    }

    private static void downloadMxLogs(CandescentClient client, String institutionId) {
        String logDate = LocalDate.now().minusDays(1).toString();
        String mxLogsHost = resolveMxLogsHost();
        ExampleHelpers.runExampleStep(
                "\n=== 4. Download MX Transaction Logs (" + logDate + ") ===",
                () -> {
                    try {
                        File logs = client.reporting()
                                .getMXReportingProxy(
                                        mxLogsHost,
                                        MX_LOGS_ACCEPT,
                                        "download/" + institutionId + "/" + logDate
                                                + "/transactions/created")
                                .execute();
                        ExampleHelpers.pretty(
                                "MX Transaction Logs",
                                Map.of(
                                        "name", logs != null ? logs.getName() : null,
                                        "length", logs != null ? logs.length() : 0));
                    } catch (ApiException ex) {
                        var mapped = CandescentClient.mapException(ex);
                        if (mapped instanceof AuthenticationException) {
                            ExampleConsole.println(
                                    "  Skipped: MX client is not authorized for transaction log"
                                            + " download in this environment");
                            return;
                        }
                        throw mapped;
                    }
                });
    }
}
