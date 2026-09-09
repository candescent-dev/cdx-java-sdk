package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.TransactionsResponse;

import java.time.LocalDate;

/**
 * Transactions example: list account transactions (mirrors examples/typescript/transactions.ts).
 */
public class TransactionsExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();

        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = ExampleHelpers.envOrNull("CANDESCENT_LOGIN_ID");
        String institutionCustomerId = ExampleHelpers.envOrNull("CANDESCENT_INSTITUTION_CUSTOMER_ID");
        String accountId = ExampleHelpers.envOrNull("CANDESCENT_ACCOUNT_ID");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised");

            accountId = ExampleHelpers.resolveAccountId(client, hostUserId, loginId, accountId);
            final String resolvedAccountId = accountId;

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);
            String dateRange = startDate + " to " + endDate;

            if (hostUserId != null && !hostUserId.isBlank()) {
                listTransactionsForUser(new TxListRequest(
                        client, "hostUserId", resolvedAccountId, dateRange, startDate, endDate,
                        hostUserId, null, null));
            } else {
                ExampleConsole.println("\n  Skipping hostUserId path (CANDESCENT_HOST_USER_ID not set)");
            }

            if (loginId != null) {
                listTransactionsForUser(new TxListRequest(
                        client, "loginId", resolvedAccountId, dateRange, startDate, endDate,
                        null, loginId, null));
            } else {
                ExampleConsole.println("\n  Skipping loginId path (CANDESCENT_LOGIN_ID not set)");
            }

            ExampleHelpers.runExampleStepWhen(
                    institutionCustomerId != null && loginId != null,
                    "\n  Skipping business transactions (CANDESCENT_INSTITUTION_CUSTOMER_ID or LOGIN_ID not set)",
                    "\n=== List Transactions — business (last 30 days) ===",
                    () -> listTransactionsForUser(new TxListRequest(
                            client,
                            "business",
                            resolvedAccountId,
                            dateRange,
                            startDate,
                            endDate,
                            null,
                            loginId,
                            institutionCustomerId)));
        }
        ExampleConsole.done();
    }

    private record TxListRequest(
            CandescentClient client,
            String label,
            String accountId,
            String dateRange,
            LocalDate startDate,
            LocalDate endDate,
            String hostUserId,
            String loginId,
            String institutionCustomerId) {}

    private static void listTransactionsForUser(TxListRequest req) {
        if ("demo-account-id".equals(req.accountId())) {
            ExampleConsole.println("\n=== List Transactions — " + req.label() + " (last 30 days) ===");
            ExampleConsole.println("  Skipping — demo account ID (set CANDESCENT_ACCOUNT_ID or fix auto-discovery)");
            return;
        }

        ExampleHelpers.runExampleStep(
                "\n=== List Transactions — " + req.label() + " (last 30 days) ===",
                () -> {
                    ExampleConsole.println("  account_id: " + req.accountId());
                    ExampleConsole.println("  date range: " + req.dateRange());
                    var builder = req.client().transactions()
                            .listAccountTransactions(req.accountId())
                            .startDate(req.startDate())
                            .endDate(req.endDate());
                    if (req.hostUserId() != null) {
                        builder.hostUserId(req.hostUserId());
                    }
                    if (req.loginId() != null) {
                        builder.loginId(req.loginId());
                    }
                    if (req.institutionCustomerId() != null) {
                        builder.institutionCustomerId(req.institutionCustomerId());
                    }
                    TransactionsResponse txns = ExampleHelpers.execute(req.client(), builder);
                    ExampleHelpers.pretty("Transaction List (" + req.label() + ")", txns);
                },
                "List transactions (" + req.label() + ")");
    }
}
