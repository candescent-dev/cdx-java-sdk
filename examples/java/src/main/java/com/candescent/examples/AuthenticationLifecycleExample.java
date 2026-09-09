package com.candescent.examples;

import com.candescent.di.CandescentClient;

/**
 * Authentication lifecycle: obtain token, use it, close (revoke), verify fresh client works.
 */
public class AuthenticationLifecycleExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");

        ExampleConsole.println("=== Step 1: Initialise client (implicit token acquisition) ===");
        CandescentClient client = ExampleHelpers.clientFromEnv();
        ExampleConsole.println("  Client initialised — token fetched on first API call");

        if (hostUserId != null && !hostUserId.isBlank()) {
            ExampleConsole.println("\n=== Step 2a: Use token — list accounts (hostUserId) ===");
            testListAccounts(client, "hostUserId", hostUserId, null);
        }
        if (loginId != null && !loginId.isBlank()) {
            ExampleConsole.println("\n=== Step 2b: Use token — list accounts (loginId) ===");
            testListAccounts(client, "loginId", null, loginId);
        }

        ExampleConsole.println("\n=== Step 3: Close client (revokes tokens) ===");
        client.close();
        ExampleConsole.println("  Client closed — tokens revoked");

        ExampleConsole.println("\n=== Step 4: Attempt API call after close ===");
        try {
            var builder = client.accounts().callList();
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            } else if (loginId != null) {
                builder.loginId(loginId);
            }
            ExampleHelpers.pretty("Accounts (after close)", ExampleHelpers.execute(client, builder));
        } catch (Exception e) {
            ExampleConsole.println("  Expected error after close: " + e);
        }

        ExampleConsole.println("\n=== Step 5: Create fresh client and verify ===");
        try (CandescentClient fresh = ExampleHelpers.clientFromEnv()) {
            if (hostUserId != null) {
                testListAccounts(fresh, "hostUserId (fresh)", hostUserId, null);
            }
            if (loginId != null) {
                testListAccounts(fresh, "loginId (fresh)", null, loginId);
            }
        }
        ExampleConsole.done();
    }

    private static void testListAccounts(
            CandescentClient client,
            String label,
            String hostUserId,
            String loginId) {
        try {
            var builder = client.accounts().callList();
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            }
            if (loginId != null) {
                builder.loginId(loginId);
            }
            ExampleHelpers.pretty("Accounts — " + label, ExampleHelpers.execute(client, builder));
            ExampleConsole.println("  SUCCESS — token is valid");
        } catch (Exception e) {
            ExampleConsole.println("  Failed: " + e);
        }
    }
}
