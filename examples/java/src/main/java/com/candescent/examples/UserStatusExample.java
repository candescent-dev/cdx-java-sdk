package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.UserStatus1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User Status: query via all four supported userIdType variants.
 */
public class UserStatusExample {
    private static final Map<String, String> ID_TYPE_ENV = Map.of(
            "HOST_USER_ID", "CANDESCENT_HOST_USER_ID",
            "LOGIN_ID", "CANDESCENT_LOGIN_ID",
            "CUSTOMER_ID", "CANDESCENT_CUSTOMER_ID",
            "INSTITUTION_USER_ID", "CANDESCENT_INSTITUTION_USER_ID");

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        List<Lookup> lookups = new ArrayList<>();
        for (var entry : ID_TYPE_ENV.entrySet()) {
            String value = System.getenv(entry.getValue());
            if (value != null && !value.isBlank()) {
                lookups.add(new Lookup(entry.getKey(), value));
            }
        }
        if (lookups.isEmpty()) {
            lookups.add(new Lookup("HOST_USER_ID", "demo-host-user"));
            ExampleConsole.println("  No user ID env vars set — using dummy HOST_USER_ID");
        }

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised");
            ExampleConsole.println("Querying " + lookups.size() + " ID type(s)");

            for (Lookup lookup : lookups) {
                ExampleConsole.println("\n=== getUserStatus — " + lookup.idType() + " ===");
                ExampleConsole.println("  userId: " + ExampleHelpers.maskSensitive(lookup.value()));
                try {
                    UserStatus1 status = ExampleHelpers.execute(client, client.profileAndStatus()
                            .getUserStatus(lookup.value(), lookup.idType()));
                    ExampleHelpers.pretty("User Status (" + lookup.idType() + ")", status);
                } catch (Exception e) {
                    ExampleConsole.println("  Failed: " + e);
                }
            }
        }
        ExampleConsole.done();
    }

    private record Lookup(String idType, String value) {}
}
