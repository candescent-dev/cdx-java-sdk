package com.candescent.examples;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Authentication operations: V1 token → auth-code flow → revoke.
 */
public class AuthenticationExample {
    private static final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_TRANSACTION_ID = "transactionId";
    private static final String MSG_SUCCESS = "  SUCCESS";
    private static final String DEFAULT_REQUESTED_SCOPES = "openid,profile,offline_access";
    private static final OkHttpClient HTTP = new OkHttpClient();

    public static void main(String[] args) {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String clientId = System.getenv("CANDESCENT_CLIENT_ID");
        String clientSecret = System.getenv("CANDESCENT_CLIENT_SECRET");
        String institutionId = System.getenv("CANDESCENT_INSTITUTION_ID");
        String oidcClientId = System.getenv("CANDESCENT_OIDC_CLIENT_ID");
        String hostUserId = System.getenv("CANDESCENT_HOST_USER_ID");
        String institutionUserId = System.getenv("CANDESCENT_INSTITUTION_USER_ID");
        String nonce = ExampleHelpers.envOrDefault("CANDESCENT_AUTH_NONCE", "candescent1234");
        String baseUrl = resolveApigeeBase();

        ExampleHelpers.requireEnvVars(List.of(
                new ExampleHelpers.EnvCheck(
                        hasAll(clientId, clientSecret, institutionId) ? "ok" : null,
                        "ERROR: Set CANDESCENT_CLIENT_ID, CANDESCENT_CLIENT_SECRET, CANDESCENT_INSTITUTION_ID"),
                new ExampleHelpers.EnvCheck(oidcClientId, "ERROR: Set CANDESCENT_OIDC_CLIENT_ID"),
                new ExampleHelpers.EnvCheck(hostUserId, "ERROR: Set CANDESCENT_HOST_USER_ID"),
                new ExampleHelpers.EnvCheck(institutionUserId, "ERROR: Set CANDESCENT_INSTITUTION_USER_ID")));

        String legacyToken = runStep1(baseUrl, clientId, clientSecret, institutionId);
        if (legacyToken == null) {
            return;
        }

        JsonObject clientAuth = runStep2(baseUrl, institutionId, legacyToken, oidcClientId, clientId);
        runStep3(new Step3Context(
                baseUrl, institutionId, legacyToken, clientAuth, oidcClientId, hostUserId, institutionUserId, nonce));
        runStep4(baseUrl, clientId, clientSecret, legacyToken);
        ExampleConsole.done();
    }

    private record Step3Context(
            String baseUrl,
            String institutionId,
            String bearer,
            JsonObject clientAuth,
            String clientId,
            String hostUserId,
            String institutionUserId,
            String nonce) {}

    private static String runStep1(String baseUrl, String clientId, String clientSecret, String institutionId) {
        ExampleConsole.println("=== Step 1: Create Legacy Token (V1) ===");
        ExampleHelpers.logEndpoint("POST /v1/oauth/token");
        try {
            String token = createV1Token(baseUrl, clientId, clientSecret, institutionId);
            ExampleHelpers.pretty("V1 Token", "{\"accessToken\":\"" + ExampleHelpers.maskSensitive(token) + "\"}");
            ExampleConsole.println(MSG_SUCCESS);
            return token;
        } catch (IOException e) {
            ExampleConsole.println("  Failed: " + e);
            ExampleConsole.println("\nDone (cannot continue without a V1 token).");
            return null;
        }
    }

    private static JsonObject runStep2(
            String baseUrl, String institutionId, String bearer, String oidcClientId, String basicClientId) {
        ExampleConsole.println("\n=== Step 2: Authorize Client ===");
        ExampleHelpers.logEndpoint("POST /auth-code/v1/client-authorization");
        List<String> candidates = oidcClientId.equals(basicClientId)
                ? List.of(oidcClientId)
                : List.of(oidcClientId, basicClientId);
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                ExampleConsole.println("  Retrying with alternate client_id...");
            }
            try {
                JsonObject auth = authorizeClient(baseUrl, institutionId, bearer, candidates.get(i));
                ExampleHelpers.pretty("ClientAuth response", auth.toString());
                ExampleConsole.println(MSG_SUCCESS);
                return auth;
            } catch (IOException e) {
                ExampleConsole.println("  " + e);
            }
        }
        return null;
    }

    private static void runStep3(Step3Context ctx) {
        ExampleConsole.println("\n=== Step 3: Get Authorization Code ===");
        ExampleHelpers.logEndpoint("POST /auth-code/v1/auth-code");
        if (ctx.clientAuth() == null || !ctx.clientAuth().has("scopes")) {
            ExampleConsole.println("  Skipped (step 2 did not return scopes).");
            return;
        }
        ExampleHelpers.runExampleStep("", () -> {
            String body = "scopes=" + urlEncode(ctx.clientAuth().get("scopes").getAsString())
                    + "&requested_scopes=" + urlEncode(DEFAULT_REQUESTED_SCOPES)
                    + "&client_id=" + urlEncode(ctx.clientId())
                    + "&username=" + urlEncode(ctx.hostUserId())
                    + "&institution_user_id=" + urlEncode(ctx.institutionUserId())
                    + "&nonce=" + urlEncode(ctx.nonce());
            Request request = new Request.Builder()
                    .url(ctx.baseUrl() + "/auth-code/v1/auth-code")
                    .post(RequestBody.create(body, FORM))
                    .header(HEADER_AUTHORIZATION, "Bearer " + ctx.bearer())
                    .header(HEADER_CONTENT_TYPE, FORM.toString())
                    .header("institutionId", ctx.institutionId())
                    .header(HEADER_TRANSACTION_ID, UUID.randomUUID().toString())
                    .build();
            try (Response response = HTTP.newCall(request).execute()) {
                String text = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new HttpExampleException("HTTP " + response.code());
                }
                ExampleHelpers.pretty("AuthCode response", text);
                ExampleConsole.println(MSG_SUCCESS);
            }
        }, "Failed (authorization-code requires OIDC config)");
    }

    private static void runStep4(String baseUrl, String clientId, String clientSecret, String token) {
        ExampleConsole.println("\n=== Step 4: Revoke Token ===");
        ExampleHelpers.logEndpoint("DELETE /oauth2/v1/revoke");
        ExampleHelpers.runExampleStep("", () -> revokeToken(baseUrl, clientId, clientSecret, token));
    }

    private static String createV1Token(String baseUrl, String clientId, String clientSecret, String institutionId)
            throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/v1/oauth/token")
                .post(RequestBody.create("grant_type=client_credentials", FORM))
                .header(HEADER_AUTHORIZATION, basicAuth(clientId, clientSecret))
                .header(HEADER_CONTENT_TYPE, FORM.toString())
                .header("di_fiid", institutionId)
                .header("di_tid", UUID.randomUUID().toString())
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new HttpExampleException("HTTP " + response.code());
            }
            if (text.trim().startsWith("<")) {
                return extractXmlTag(text, "access_token");
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            return json.get("access_token").getAsString();
        }
    }

    private static JsonObject authorizeClient(String baseUrl, String institutionId, String bearer, String clientId)
            throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/auth-code/v1/client-authorization")
                .post(RequestBody.create("client_id=" + urlEncode(clientId), FORM))
                .header(HEADER_AUTHORIZATION, "Bearer " + bearer)
                .header(HEADER_CONTENT_TYPE, FORM.toString())
                .header("institutionId", institutionId)
                .header(HEADER_TRANSACTION_ID, UUID.randomUUID().toString())
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new HttpExampleException("HTTP " + response.code());
            }
            return JsonParser.parseString(text).getAsJsonObject();
        }
    }

    private static void revokeToken(String baseUrl, String clientId, String clientSecret, String token)
            throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/oauth2/v1/revoke")
                .delete(RequestBody.create("token=" + urlEncode(token), FORM))
                .header(HEADER_AUTHORIZATION, basicAuth(clientId, clientSecret))
                .header(HEADER_CONTENT_TYPE, FORM.toString())
                .header(HEADER_TRANSACTION_ID, UUID.randomUUID().toString())
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpExampleException("HTTP " + response.code());
            }
            ExampleConsole.println("  Token revoked successfully");
        }
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

    private static String basicAuth(String clientId, String clientSecret) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    private static String extractXmlTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        if (start == -1) {
            throw new HttpExampleException("missing " + tag);
        }
        int valueStart = start + open.length();
        int end = xml.indexOf(close, valueStart);
        return xml.substring(valueStart, end);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasAll(String... values) {
        for (String v : values) {
            if (v == null || v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static final class HttpExampleException extends RuntimeException {
        HttpExampleException(String message) {
            super(message);
        }
    }
}
