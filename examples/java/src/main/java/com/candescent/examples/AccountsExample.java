package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.AccountsResponse;

/**
 * Accounts example: list accounts and get a single account by ID.
 *
 * <p>Prerequisites: set CANDESCENT_CLIENT_ID, CANDESCENT_CLIENT_SECRET,
 * CANDESCENT_INSTITUTION_ID (and optionally CANDESCENT_ENVIRONMENT).
 *
 * <p>Run from repo root after building the SDK:
 * <pre>{@code
 * cd sdks/java && mvn install -DskipTests
 * cd examples/java
 * mvn exec:java -Dexec.mainClass="com.candescent.examples.AccountsExample"
 * }</pre>
 */
public class AccountsExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();

        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");
        String accountId = System.getenv("CANDESCENT_ACCOUNT_ID");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised (V1 + V2 dual-token routing enabled)");

            if (hostUserId != null && !hostUserId.isBlank()) {
                accountId = listAccountsAndDiscoverId(client, "hostUserId", hostUserId, null, accountId);
            } else {
                ExampleConsole.println("\n  Skipping hostUserId path (CANDESCENT_HOST_USER_ID not set)");
            }

            if (loginId != null && !loginId.isBlank()) {
                accountId = listAccountsAndDiscoverId(client, "loginId", null, loginId, accountId);
            } else {
                ExampleConsole.println("\n  Skipping loginId path (CANDESCENT_LOGIN_ID not set)");
            }

            if (loginId != null && !loginId.isBlank()) {
                runBusinessLists(client, loginId);
            }

            if (accountId != null && hostUserId != null) {
                getSingleAccount(client, accountId, "hostUserId", hostUserId, null);
            }
            if (accountId != null && loginId != null) {
                getSingleAccount(client, accountId, "loginId", null, loginId);
            }
            if (accountId == null) {
                ExampleConsole.println("\n  Skipping get single account (no account_id available)");
            }
        }

        ExampleConsole.done();
    }

    private static String listAccountsAndDiscoverId(
            CandescentClient client,
            String label,
            String hostUserId,
            String loginId,
            String accountId) {
        final String[] discovered = {accountId};
        ExampleHelpers.runExampleStep("\n=== List Accounts (via " + label + ") ===", () -> {
            var builder = client.accounts().callList();
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            }
            if (loginId != null) {
                builder.loginId(loginId);
            }
            AccountsResponse accounts = ExampleHelpers.execute(client, builder);
            ExampleHelpers.pretty("Account List (" + label + ")", accounts);
            if (discovered[0] == null
                    && accounts.getAccounts() != null
                    && !accounts.getAccounts().isEmpty()
                    && accounts.getAccounts().get(0).getId() != null) {
                discovered[0] = accounts.getAccounts().get(0).getId();
                ExampleConsole.println("\n  Auto-discovered account_id: " + discovered[0]);
            }
        }, "List accounts (" + label + ")");
        return discovered[0];
    }

    private static void runBusinessLists(CandescentClient client, String loginId) {
        ExampleHelpers.runExampleStep("\n=== Business: List Accounts (grouped by customer) ===", () -> {
            AccountsResponse bbAccounts = ExampleHelpers.execute(client, client.accounts()
                    .callList()
                    .loginId(loginId)
                    .$apply("groupBy(customer)")
                    .$skipGroups(0)
                    .$topGroups(1));
            ExampleHelpers.pretty("Business Account List (grouped)", bbAccounts);
        });

        ExampleHelpers.runExampleStep("\n=== Business: List Accounts (filtered by category) ===", () -> {
            AccountsResponse bbFiltered = ExampleHelpers.execute(client, client.accounts()
                    .callList()
                    .loginId(loginId)
                    .$filter("category eq 'DEPOSIT'"));
            ExampleHelpers.pretty("Business Account List (DEPOSIT only)", bbFiltered);
        });
    }

    private static void getSingleAccount(
            CandescentClient client,
            String accountId,
            String label,
            String hostUserId,
            String loginId) {
        ExampleHelpers.runExampleStep("\n=== Get Single Account (via " + label + ") ===", () -> {
            var builder = client.accounts().getAccountById(accountId);
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            }
            if (loginId != null) {
                builder.loginId(loginId);
            }
            ExampleHelpers.pretty(
                    "Single Account — " + label + " (" + accountId + ")",
                    ExampleHelpers.execute(client, builder));
        });
    }
}
