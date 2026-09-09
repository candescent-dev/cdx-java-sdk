package com.candescent.examples;

import com.candescent.di.CandescentClient;

import java.time.LocalDate;

/**
 * Business payments: list ACH and wire payments.
 */
public class BusinessPaymentsExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String loginId = ExampleHelpers.envOrDefault("CANDESCENT_BB_LOGIN_ID", "demo-login-id");
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(30);

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleHelpers.runExampleStep("=== List ACH Payments ===", () ->
                    ExampleHelpers.pretty("ACH Payments", 
                            ExampleHelpers.execute(client, client.payments().getAchPayments(loginId, fromDate, toDate))));

            ExampleHelpers.runExampleStep("\n=== List Wire Payments ===", () ->
                    ExampleHelpers.pretty("Wire Payments", 
                            ExampleHelpers.execute(client, client.payments().getWirePayments(loginId, fromDate, toDate))));
        }
        ExampleConsole.done();
    }
}
