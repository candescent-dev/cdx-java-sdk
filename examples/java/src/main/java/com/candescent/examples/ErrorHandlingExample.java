package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.errors.ApiErrorException;
import com.candescent.di.errors.BadRequestException;
import com.candescent.di.errors.NotFoundException;
import com.candescent.di.generated.ApiException;

/**
 * Error handling patterns: typed exceptions mapped from ApiException.
 */
public class ErrorHandlingExample {
    private static final String SEPARATOR = "=".repeat(72);
    private static final String ENV_HOST_USER_ID = "CANDESCENT_HOST_USER_ID";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        ExampleConsole.println(SEPARATOR);
        ExampleConsole.println("  Candescent SDK — Error Handling Patterns (Java)");
        ExampleConsole.println(SEPARATOR);

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised\n");
            demoSuccessPath(client);
            demoNotFound(client);
            demoBadRequest(client);
            demoCatchAll(client);
            demoErrorFields(client);
            demoRateLimitPattern();
            demoProductionPattern();
        }

        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.done("  Done.");
        ExampleConsole.println(SEPARATOR);
    }

    private static void demoSuccessPath(CandescentClient client) throws Exception {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 1: Success path — list accounts");
        ExampleConsole.println(SEPARATOR);

        String hostUserId = System.getenv(ENV_HOST_USER_ID);
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");
        if ((hostUserId == null || hostUserId.isBlank()) && (loginId == null || loginId.isBlank())) {
            ExampleConsole.println("  Skipped (set CANDESCENT_HOST_USER_ID or CANDESCENT_LOGIN_ID)");
            return;
        }

        try {
            var builder = client.accounts().callList();
            if (hostUserId != null && !hostUserId.isBlank()) {
                builder.hostUserId(hostUserId);
            } else {
                builder.loginId(loginId);
            }
            ExampleHelpers.pretty("Accounts retrieved successfully", ExampleHelpers.execute(client, builder));
        } catch (ApiException ex) {
            ExampleConsole.println("  Unexpected API error: " + CandescentClient.mapException(ex).getMessage());
        }
    }

    private static void demoNotFound(CandescentClient client) {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 2: NotFoundError — get account with fake ID");
        ExampleConsole.println(SEPARATOR);

        String fakeId = "00000000-0000-0000-0000-000000000000";
        String hostUserId = System.getenv(ENV_HOST_USER_ID);
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");

        try {
            var builder = client.accounts().getAccountById(fakeId);
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            } else if (loginId != null) {
                builder.loginId(loginId);
            }
            ExampleHelpers.execute(client, builder);
        } catch (ApiException ex) {
            ApiErrorException mapped = CandescentClient.mapException(ex);
            if (mapped instanceof NotFoundException nf) {
                ExampleConsole.println("  Caught NotFoundError (HTTP " + nf.getStatusCode() + "): " + nf.getMessage());
            } else {
                ExampleConsole.println("  Got different API error: HTTP " + mapped.getStatusCode());
            }
            dumpApiError(mapped);
        }
    }

    private static void demoBadRequest(CandescentClient client) {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 3: BadRequestError — empty identifiers");
        ExampleConsole.println(SEPARATOR);
        try {
            ExampleHelpers.execute(client, client.accounts().callList());
            ExampleConsole.println("  Unexpected success");
        } catch (ApiException ex) {
            ApiErrorException mapped = CandescentClient.mapException(ex);
            if (mapped instanceof BadRequestException br) {
                ExampleConsole.println("  Caught BadRequestError: " + br.getMessage());
            } else {
                ExampleConsole.println("  Got API error HTTP " + mapped.getStatusCode());
            }
            dumpApiError(mapped);
        }
    }

    private static void demoCatchAll(CandescentClient client) {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 4: Catch-all ApiError");
        ExampleConsole.println(SEPARATOR);
        try {
            ExampleHelpers.execute(client, client.accounts().getAccountById("not-a-valid-id").hostUserId("invalid"));
        } catch (ApiException ex) {
            ApiErrorException mapped = CandescentClient.mapException(ex);
            ExampleConsole.println("  Caught " + mapped.getClass().getSimpleName() + " HTTP " + mapped.getStatusCode());
            dumpApiError(mapped);
        }
    }

    private static void demoErrorFields(CandescentClient client) {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 5: Inspecting ApiError fields");
        ExampleConsole.println(SEPARATOR);
        try {
            var builder = client.accounts().getAccountById("00000000-0000-0000-0000-000000000000");
            String hostUserId = System.getenv(ENV_HOST_USER_ID);
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            }
            ExampleHelpers.execute(client, builder);
        } catch (ApiException ex) {
            ApiErrorException err = CandescentClient.mapException(ex);
            ExampleConsole.println("    statusCode → " + err.getStatusCode());
            ExampleConsole.println("    message    → " + err.getMessage());
            ExampleConsole.println("    body       → " + (err.getBody() != null ? err.getBody().substring(0, Math.min(120, err.getBody().length())) : ""));
            ExampleConsole.println("    instanceof NotFoundException → " + (err instanceof NotFoundException));
        }
    }

    private static void dumpApiError(ApiErrorException err) {
        ExampleConsole.println("    statusCode: " + err.getStatusCode());
        ExampleConsole.println("    body:       " + (err.getBody() != null ? err.getBody().substring(0, Math.min(200, err.getBody().length())) : ""));
    }

    private static void demoRateLimitPattern() {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 6: RateLimitError — retry-after pattern (reference)");
        ExampleConsole.println(SEPARATOR);
        ExampleConsole.println("""
          On HTTP 429, map ApiException and check instanceof RateLimitException.
          Use getRetryAfter() from the Retry-After header, or exponential backoff.
        """);
    }

    private static void demoProductionPattern() {
        ExampleConsole.println("\n" + SEPARATOR);
        ExampleConsole.println("  SCENARIO 7: Production-grade combined handler (reference)");
        ExampleConsole.println(SEPARATOR);
        ExampleConsole.println("""
          catch (ApiException ex) {
            ApiErrorException err = CandescentClient.mapException(ex);
            if (err instanceof NotFoundException) { ... }
            else if (err instanceof BadRequestException) { ... }
            else if (err instanceof AuthenticationException) { ... }
            ...
          }
        """);
    }
}
