package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.errors.CandescentException;
import com.candescent.di.generated.ApiCallback;
import com.candescent.di.generated.ApiClient;
import com.candescent.di.generated.ApiException;
import com.candescent.di.generated.ApiResponse;
import com.candescent.di.generated.JSON;
import com.candescent.di.generated.Pair;
import com.candescent.di.generated.model.Account;
import com.candescent.di.generated.model.AccountsResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import okhttp3.Call;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared utilities for Candescent DI Java examples (mirrors examples/typescript/_helpers.ts).
 */
public final class ExampleHelpers {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String APPLICATION_JSON = "application/json";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String PATH_SEPARATOR = "/";
    static final String JSON_RECIPIENTS = "recipients";
    static final String JSON_RECIPIENTS_PASCAL = "Recipients";
    private static final Set<String> KNOWN_ENDPOINTS = Set.of(
            "POST /auth-code/v1/client-authorization",
            "POST /v1/oauth/token",
            "POST /auth-code/v1/auth-code",
            "POST /oauth2/v1/token",
            "DELETE /oauth2/v1/revoke");

    private ExampleHelpers() {}

    // Single offset section: two optional appendOffset blocks both emit "Z" for UTC and produce "...ZZ".
    private static final DateTimeFormatter FLEX_OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .appendOffset("+HH:MM", "Z")
            .toFormatter();

    // API sometimes returns offsets without a colon (e.g. -0700); Gson expects -07:00.
    private static final Pattern OFFSET_WITHOUT_COLON = Pattern.compile(
            "(\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)([+-]\\d{2})(\\d{2})(\")");

    private static String normalizeLenientOffsetDateTimes(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return jsonBody;
        }
        return OFFSET_WITHOUT_COLON.matcher(jsonBody).replaceAll("$1$2:$3$4");
    }

    /** Build client from env with example-friendly API parsing (mirrors TS tolerance). */
    public static CandescentClient clientFromEnv() {
        CandescentClient client = CandescentClient.fromEnv();
        configureClientParsing(client);
        return client;
    }

    /** Lenient date parsing and API field tolerance for example clients. */
    public static void configureClientParsing(CandescentClient client) {
        ApiClient apiClient = client.getApiClient();
        apiClient.setOffsetDateTimeFormat(FLEX_OFFSET_DATE_TIME);
        apiClient.setLenientOnJson(true);
    }

    /**
     * Let OkHttp HTTP/2 background threads finish before {@code exec:java} tears down its classpath.
     * Call via {@link ExampleConsole#done()} at the end of runnable examples.
     */
    public static void quiesceOkHttp() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute a generated API request builder and deserialize with OpenAPI field tolerance.
     * Strips response properties absent from generated models before Gson validation runs.
     */
    public static String executeRaw(CandescentClient client, Object requestBuilder) throws ApiException {
        ApiResponse<String> raw =
                client.getApiClient().execute(buildCall(requestBuilder), String.class);
        return raw.getData();
    }

    @SuppressWarnings("unchecked")
    public static <T> T execute(CandescentClient client, Object requestBuilder) throws ApiException {
        try {
            Method executeMethod = requestBuilder.getClass().getMethod("execute");
            Class<?> returnType = executeMethod.getReturnType();
            if (returnType == void.class) {
                executeVoid(client, requestBuilder);
                return null;
            }
            return execute(client, requestBuilder, (Class<T>) returnType);
        } catch (NoSuchMethodException ex) {
            throw new ApiException(ex);
        }
    }

    public static <T> T execute(CandescentClient client, Object requestBuilder, Class<T> returnType)
            throws ApiException {
        ApiResponse<String> raw =
                client.getApiClient().execute(buildCall(requestBuilder), String.class);
        try {
            return deserializeTolerant(client, raw.getData(), returnType);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(ex);
        }
    }

    /** Execute a generated API request builder with no response body. */
    public static void executeVoid(CandescentClient client, Object requestBuilder) throws ApiException {
        client.getApiClient().execute(buildCall(requestBuilder));
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserializeTolerant(String jsonBody, Class<T> returnType) throws ApiException {
        return deserializeTolerant(
                JSON.getGson(), normalizeApiJsonAliases(normalizeLenientOffsetDateTimes(jsonBody), returnType), returnType);
    }

    /** Normalize known stage API response key aliases before Gson binding. */
    private static String normalizeApiJsonAliases(String jsonBody, Class<?> returnType) {
        if (jsonBody == null || jsonBody.isBlank() || returnType == null) {
            return jsonBody;
        }
        try {
            JsonElement tree = JsonParser.parseString(jsonBody);
            if (!tree.isJsonObject()) {
                return jsonBody;
            }
            JsonObject obj = tree.getAsJsonObject();
            if (com.candescent.di.generated.model.Recipients.class.isAssignableFrom(returnType)) {
                obj = normalizeRecipientsObject(obj);
            }
            return obj.toString();
        } catch (RuntimeException ex) {
            return jsonBody;
        }
    }

    private static JsonObject normalizeRecipientsObject(JsonObject obj) {
        if (!obj.has(JSON_RECIPIENTS) && obj.has(JSON_RECIPIENTS_PASCAL)) {
            obj.add(JSON_RECIPIENTS, obj.get(JSON_RECIPIENTS_PASCAL));
        }
        if ((!obj.has(JSON_RECIPIENTS) || obj.get(JSON_RECIPIENTS).isJsonNull())
                && obj.has("id")
                && obj.get("id").isJsonPrimitive()) {
            JsonArray recipients = new JsonArray();
            recipients.add(obj.deepCopy());
            obj = new JsonObject();
            obj.add(JSON_RECIPIENTS, recipients);
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserializeTolerant(CandescentClient client, String jsonBody, Class<T> returnType)
            throws ApiException {
        return deserializeTolerant(
                client.getApiClient().getJSON().getGson(),
                normalizeApiJsonAliases(normalizeLenientOffsetDateTimes(jsonBody), returnType),
                returnType);
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserializeTolerant(Gson gson, String jsonBody, Class<T> returnType) throws ApiException {
        if (jsonBody == null || jsonBody.isBlank()) {
            return null;
        }
        try {
            if (Map.class.isAssignableFrom(returnType)) {
                Type mapType = TypeToken.getParameterized(Map.class, String.class, Object.class).getType();
                return (T) gson.fromJson(jsonBody, mapType);
            }
            JsonElement tree = JsonParser.parseString(jsonBody);
            JsonElement stripped = stripUnknownFields(tree, returnType);
            String payload = stripped.toString();
            return deserializeModel(gson, payload, returnType);
        } catch (JsonParseException ex) {
            try {
                return gson.fromJson(jsonBody, returnType);
            } catch (RuntimeException fallbackEx) {
                throw new ApiException(ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserializeModel(Gson gson, String jsonBody, Class<T> returnType) throws ApiException {
        try {
            Method fromJson = returnType.getMethod("fromJson", String.class);
            return invokeFromJsonOrGson(fromJson, returnType, gson, jsonBody);
        } catch (NoSuchMethodException ex) {
            return gson.fromJson(jsonBody, returnType);
        }
    }

    private static <T> T invokeFromJsonOrGson(Method fromJson, Class<T> returnType, Gson gson, String jsonBody)
            throws ApiException {
        try {
            return returnType.cast(fromJson.invoke(null, jsonBody));
        } catch (java.lang.reflect.InvocationTargetException ex) {
            return gson.fromJson(jsonBody, returnType);
        } catch (IllegalAccessException ex) {
            throw new ApiException(ex);
        }
    }

    private static Call buildCall(Object requestBuilder) throws ApiException {
        try {
            return (Call) requestBuilder.getClass()
                    .getMethod("buildCall", ApiCallback.class)
                    .invoke(requestBuilder, (Object) null);
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ApiException apiEx) {
                throw apiEx;
            }
            throw new ApiException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> openApiFields(Class<?> clazz) {
        if (clazz == null) {
            return Set.of();
        }
        try {
            Field field = clazz.getField("openapiFields");
            Set<String> fields = (Set<String>) field.get(null);
            return fields != null ? fields : Set.of();
        } catch (ReflectiveOperationException ex) {
            return Set.of();
        }
    }

    private static JsonElement stripUnknownFields(JsonElement element, Class<?> modelClass) {
        if (element == null || element.isJsonNull() || modelClass == null) {
            return element;
        }
        if (element.isJsonArray()) {
            return stripUnknownFieldsFromArray(element.getAsJsonArray(), modelClass);
        }
        if (!element.isJsonObject()) {
            return element;
        }
        Set<String> allowed = openApiFields(modelClass);
        if (allowed.isEmpty()) {
            return element;
        }
        return stripUnknownFieldsFromObject(element.getAsJsonObject(), modelClass, allowed);
    }

    private static JsonElement stripUnknownFieldsFromArray(JsonArray array, Class<?> modelClass) {
        JsonArray cleaned = new JsonArray();
        for (JsonElement item : array) {
            cleaned.add(stripUnknownFields(item, modelClass));
        }
        return cleaned;
    }

    private static JsonElement stripUnknownFieldsFromObject(
            JsonObject source, Class<?> modelClass, Set<String> allowed) {
        JsonObject result = new JsonObject();
        for (String key : allowed) {
            if (!source.has(key)) {
                continue;
            }
            JsonElement child = source.get(key);
            Class<?> nested = fieldTypeForJsonKey(modelClass, key);
            if (nested != null && child != null && child.isJsonArray()) {
                result.add(key, stripUnknownFieldsFromArray(child.getAsJsonArray(), nested));
            } else {
                result.add(key, stripUnknownFields(child, nested));
            }
        }
        return result;
    }

    private static Class<?> fieldTypeForJsonKey(Class<?> parent, String jsonKey) {
        for (Field field : parent.getDeclaredFields()) {
            String serialized = serializedName(parent, field);
            if (serialized != null && serialized.equals(jsonKey)) {
                return unwrapType(field.getGenericType());
            }
        }
        return null;
    }

    private static Class<?> unwrapType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> rawClass && Collection.class.isAssignableFrom(rawClass)) {
                Type[] args = parameterized.getActualTypeArguments();
                if (args.length == 1 && args[0] instanceof Class<?> argClass) {
                    return argClass;
                }
            }
            if (raw instanceof Class<?> rawClass) {
                return rawClass;
            }
        }
        return null;
    }

    private static String serializedName(Class<?> clazz, Field field) {
        String constName = "SERIALIZED_NAME_" + field.getName().toUpperCase(Locale.ROOT);
        try {
            Field constant = clazz.getField(constName);
            if (constant.getType() == String.class) {
                return (String) constant.get(null);
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return null;
    }

    /** Merge OpenAPI model JSON with extra request fields omitted from generated types. */
    public static JsonObject toRequestJson(Object model, Map<String, ?> extras) {
        JsonObject json = JSON.getGson().toJsonTree(model).getAsJsonObject();
        if (extras != null) {
            extras.forEach((key, value) -> json.add(key, JSON.getGson().toJsonTree(value)));
        }
        return json;
    }

    public static <T> T postJson(
            CandescentClient client, String path, JsonObject body, Class<T> returnType)
            throws ApiException {
        return postJson(client, path, body, List.of(), Map.of(), returnType);
    }

    public static <T> T postJson(
            CandescentClient client,
            String path,
            JsonObject body,
            List<Pair> queryParams,
            Class<T> returnType)
            throws ApiException {
        return postJson(client, path, body, queryParams, Map.of(), returnType);
    }

    public static <T> T postJson(
            CandescentClient client,
            String path,
            JsonObject body,
            List<Pair> queryParams,
            Map<String, String> headerParams,
            Class<T> returnType)
            throws ApiException {
        ApiClient apiClient = client.getApiClient();
        Call call = apiClient.buildCall(
                apiClient.getBasePath(),
                path,
                "POST",
                queryParams,
                List.of(),
                body,
                jsonPostHeaders(headerParams),
                Map.of(),
                Map.of(),
                new String[] {},
                null);
        ApiResponse<String> response = apiClient.execute(call, String.class);
        try {
            return deserializeTolerant(client, response.getData(), returnType);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(ex);
        }
    }

    public static void postJsonVoid(
            CandescentClient client, String path, JsonObject body, List<Pair> queryParams)
            throws ApiException {
        postJsonVoid(client, path, body, queryParams, Map.of());
    }

    /** POST with no response body (e.g. 202 Accepted); skips Gson deserialization. */
    public static void postJsonVoid(
            CandescentClient client,
            String path,
            JsonObject body,
            List<Pair> queryParams,
            Map<String, String> headerParams)
            throws ApiException {
        ApiClient apiClient = client.getApiClient();
        Call call = apiClient.buildCall(
                apiClient.getBasePath(),
                path,
                "POST",
                queryParams,
                List.of(),
                body,
                jsonPostHeaders(headerParams),
                Map.of(),
                Map.of(),
                new String[] {},
                null);
        apiClient.execute(call);
    }

    /**
     * MX SSO/Platform proxy POST where {@code resourcePath} may contain slashes and must not be
     * URL-encoded as a single path segment (generated clients use {@code escapeString}).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> postMxProxy(
            CandescentClient client,
            String institutionId,
            String resourcePath,
            Object body,
            Map<String, String> headerParams)
            throws ApiException {
        ApiClient apiClient = client.getApiClient();
        String path = PATH_SEPARATOR + "mx" + PATH_SEPARATOR + institutionId + PATH_SEPARATOR + resourcePath;
        Call call = apiClient.buildCall(
                apiClient.getBasePath(),
                path,
                "POST",
                List.of(),
                List.of(),
                body,
                jsonPostHeaders(headerParams),
                Map.of(),
                Map.of(),
                new String[] {},
                null);
        ApiResponse<String> response = apiClient.execute(call, String.class);
        String payload = response.getData();
        if (payload != null && payload.trim().startsWith("<")) {
            return Map.of("rawResponse", payload.trim());
        }
        return deserializeTolerant(client, payload, Map.class);
    }

    private static Map<String, String> jsonPostHeaders(Map<String, String> headerParams) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_ACCEPT, APPLICATION_JSON);
        headers.put(HEADER_CONTENT_TYPE, APPLICATION_JSON);
        if (headerParams != null) {
            headers.putAll(headerParams);
        }
        return headers;
    }

    /** Demo fallbacks when no client credentials are configured (mirrors _helpers.ts). */
    public static void applyDemoFallbacksIfNeeded() {
        if (System.getenv("CANDESCENT_CLIENT_ID") == null
                && System.getenv("CANDESCENT_CLIENT_SECRET") == null) {
            setDefaultIfUnset("CANDESCENT_INSTITUTION_ID", "05523");
            setDefaultIfUnset("CANDESCENT_BEARER_TOKEN", "demo-bearer-token");
        }
    }

    public static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    public static String envOrNull(String key) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : null;
    }

    /** Channel password for registration demos; set DEMO_REGISTRATION_CHANNEL_PASSWORD in .env. */
    public static String registrationChannelPassword() {
        String fromEnv = envOrNull("DEMO_REGISTRATION_CHANNEL_PASSWORD");
        if (fromEnv != null) {
            return fromEnv;
        }
        return "Test" + secureRandomInt(100000, 999999) + "@Aa1";
    }

    /** Host credential password for registration demos; set DEMO_REGISTRATION_HOST_PASSWORD in .env. */
    public static String registrationHostPassword() {
        String fromEnv = envOrNull("DEMO_REGISTRATION_HOST_PASSWORD");
        if (fromEnv != null) {
            return fromEnv;
        }
        return String.valueOf(secureRandomInt(1_000_000, 10_000_000));
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String resolveAccountId(
            CandescentClient client, String hostUserId, String loginId, String accountId) {
        return resolveAccountId(client, hostUserId, loginId, accountId, null);
    }

    /** Resolve account id from env, or by account number, or first listed account. */
    public static String resolveAccountId(
            CandescentClient client,
            String hostUserId,
            String loginId,
            String accountId,
            String accountNumber) {
        if (accountId != null && !accountId.isBlank()) {
            return accountId;
        }
        ExampleConsole.println("\nAuto-discovering account_id from list...");
        try {
            return discoverAccountId(client, hostUserId, loginId, accountNumber);
        } catch (Exception e) {
            ExampleConsole.println("  Auto-discovery failed: " + e + " — using demo account ID");
            return "demo-account-id";
        }
    }

    private static String discoverAccountId(
            CandescentClient client, String hostUserId, String loginId, String accountNumber)
            throws ApiException {
        var builder = client.accounts().callList();
        if (hostUserId != null && !hostUserId.isBlank()) {
            builder.hostUserId(hostUserId);
        } else if (loginId != null && !loginId.isBlank()) {
            builder.loginId(loginId);
        }
        AccountsResponse accounts = execute(client, builder, AccountsResponse.class);
        String discovered = pickAccountIdFromResponse(accounts, accountNumber);
        if (discovered != null) {
            return discovered;
        }
        ExampleConsole.println("  No accounts found — using demo account ID");
        return "demo-account-id";
    }

    private static String pickAccountIdFromResponse(AccountsResponse accounts, String accountNumber) {
        if (accounts.getAccounts() == null || accounts.getAccounts().isEmpty()) {
            return null;
        }
        if (accountNumber != null && !accountNumber.isBlank()) {
            String byNumber = findAccountIdByNumber(accounts.getAccounts(), accountNumber);
            if (byNumber != null) {
                return byNumber;
            }
            ExampleConsole.println(
                    "  Account number " + accountNumber + " not found — using first account");
        }
        String firstId = accounts.getAccounts().get(0).getId();
        if (firstId != null) {
            ExampleConsole.println("  Using account_id: " + firstId);
            return firstId;
        }
        return null;
    }

    private static String findAccountIdByNumber(List<Account> accounts, String accountNumber) {
        for (var account : accounts) {
            if (accountNumber.equals(account.getAccountNumber()) && account.getId() != null) {
                ExampleConsole.println(
                        "  Using account_id: " + account.getId()
                                + " (accountNumber=" + accountNumber + ")");
                return account.getId();
            }
        }
        return null;
    }

    private static void setDefaultIfUnset(String key, String value) {
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    public static void logEndpoint(String endpoint) {
        if (KNOWN_ENDPOINTS.contains(endpoint)) {
            ExampleConsole.println("  Endpoint: " + endpoint);
        } else {
            ExampleConsole.println("  Endpoint: [redacted]");
        }
    }

    public static String envSetStatus(String value) {
        return value != null && !value.isBlank() ? "(set)" : "(not set)";
    }

    public static String redact(String value) {
        return value != null && !value.isBlank() ? "***redacted***" : "(not set)";
    }

    public static String maskSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    public static String assertCandescentHttpsBase(String baseUrl) {
        String normalized = baseUrl.replaceAll("/$", "");
        if (isLocalMockHttpBase(normalized)) {
            return normalized;
        }
        URI parsed;
        try {
            parsed = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new CandescentException("Invalid API base URL");
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            throw new CandescentException("API base URL must use HTTPS");
        }
        String host = parsed.getHost() != null ? parsed.getHost().toLowerCase() : "";
        if (!"api.candescent.com".equals(host) && !host.endsWith(".candescent.com")) {
            throw new CandescentException("Disallowed API base URL host: " + parsed.getHost());
        }
        return normalized;
    }

    private static boolean isLocalMockHttpBase(String baseUrl) {
        if (!"1".equals(System.getenv("CANDESCENT_MOCK_HTTP"))) {
            return false;
        }
        URI parsed;
        try {
            parsed = URI.create(baseUrl.replaceAll("/$", ""));
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = parsed.getHost() != null ? parsed.getHost().toLowerCase() : "";
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }

    public static int secureRandomInt(int minInclusive, int maxExclusive) {
        return RANDOM.nextInt(maxExclusive - minInclusive) + minInclusive;
    }

    public static <T> T secureRandomChoice(List<T> items) {
        return items.get(RANDOM.nextInt(items.size()));
    }

    public static String toPrettyJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return s;
        }
        return new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(JSON.getGson().toJsonTree(value));
    }

    public static void pretty(String label, Object obj) {
        ExampleConsole.println("\n" + "-".repeat(72));
        ExampleConsole.println("  " + label);
        ExampleConsole.println("-".repeat(72));
        ExampleConsole.println(toPrettyJson(obj));
    }

    public static void runExampleStep(String heading, ThrowingRunnable fn) {
        runExampleStep(heading, fn, "Failed");
    }

    public static void runExampleStep(String heading, ThrowingRunnable fn, String failureLabel) {
        ExampleConsole.println(heading);
        try {
            fn.run();
        } catch (Exception e) {
            ExampleConsole.println("  " + failureLabel + ": " + formatExampleFailure(e));
        }
    }

    private static String formatExampleFailure(Throwable error) {
        if (error instanceof IllegalStateException && error.getCause() != null) {
            return error + " — caused by: " + error.getCause();
        }
        return String.valueOf(error);
    }

    public static void runExampleStepWhen(
            boolean condition, String skipMessage, String heading, ThrowingRunnable fn) {
        runExampleStepWhen(condition, skipMessage, heading, fn, "Failed");
    }

    public static void runExampleStepWhen(
            boolean condition,
            String skipMessage,
            String heading,
            ThrowingRunnable fn,
            String failureLabel) {
        if (!condition) {
            ExampleConsole.println(skipMessage);
            return;
        }
        runExampleStep(heading, fn, failureLabel);
    }

    public static void runExpectedErrorStep(String heading, ThrowingRunnable fn) {
        ExampleConsole.println(heading);
        try {
            fn.run();
            ExampleConsole.println("  UNEXPECTED SUCCESS — should have returned an error");
        } catch (Exception e) {
            ExampleConsole.println("  Expected error: " + e);
        }
    }

    public static void requireEnvVars(List<EnvCheck> checks) {
        for (EnvCheck check : checks) {
            if (check.value() == null || check.value().isBlank()) {
                ExampleConsole.errPrintln(check.message());
                System.exit(1);
            }
        }
    }

    public static List<EnvCheck> envChecks(String... pairs) {
        List<EnvCheck> checks = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            checks.add(new EnvCheck(pairs[i], pairs[i + 1]));
        }
        return checks;
    }

    public record EnvCheck(String value, String message) {}

    @FunctionalInterface
    @SuppressWarnings("java:S112")
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
