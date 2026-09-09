package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.Event;
import com.candescent.di.generated.model.Events;
import com.candescent.di.generated.model.Subscription;

import java.util.List;

/**
 * Notification channels (mirrors examples/typescript/notification-channels.ts).
 */
public class NotificationChannelsExample {
    private static final String EVENT_BALANCE_THRESHOLD = "BALANCE_THRESHOLD";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();

        String fiId = ExampleHelpers.envOrDefault("CANDESCENT_INSTITUTION_ID", "05523");
        String customerId = ExampleHelpers.firstNonBlank(
                System.getenv("CANDESCENT_INSTITUTION_CUSTOMER_ID"),
                System.getenv("CANDESCENT_CUSTOMER_ID"),
                System.getenv("CANDESCENT_HOST_USER_ID"),
                "demo-host-user");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("  FI_ID (diFiid):        " + fiId);
            ExampleConsole.println("  CUSTOMER_ID:           " + ExampleHelpers.envSetStatus(customerId));
            ExampleConsole.println("Client initialised (V1 token routing for /subscriptions/v1/...)\n");

            ExampleHelpers.runExampleStep("\n=== 1. List Institution Subscriptions ===", () -> {
                List<Subscription> subs = client.notificationChannels().listInstitutionSubscriptions(fiId).execute();
                ExampleHelpers.pretty("Institution Subscriptions", subs);
                ExampleConsole.println("\n  Total institution subscriptions returned: " + (subs != null ? subs.size() : 0));
            }, "List institution subscriptions");

            ExampleHelpers.runExampleStep("\n=== 2. List User Subscriptions ===", () -> {
                List<Subscription> subs = client.notificationChannels().listUserSubscriptions(fiId, customerId).execute();
                ExampleHelpers.pretty("User Subscriptions", subs);
                ExampleConsole.println("\n  Total user subscriptions returned: " + (subs != null ? subs.size() : 0));
            }, "List user subscriptions");

            final String[] subscriptionId = {null};
            ExampleHelpers.runExampleStep("\n=== 3. Create Subscription ===", () -> {
                Subscription newSub = new Subscription();
                newSub.setFiId(fiId);
                newSub.setFiCustomerId(customerId);
                newSub.setEventTypeId(EVENT_BALANCE_THRESHOLD);
                Subscription created = ExampleHelpers.execute(client, client.notificationChannels()
                        .createSubscription(fiId, customerId, newSub));
                ExampleHelpers.pretty("Created Subscription", created);
                if (created != null && created.getId() != null) {
                    subscriptionId[0] = created.getId();
                    ExampleConsole.println("\n  -> Subscription ID: " + subscriptionId[0]);
                }
            }, "Create subscription");

            if (subscriptionId[0] == null) {
                ExampleConsole.println("\n  No subscription ID available — skipping get/update/delete steps.");
                sendCustomerEvent(client, fiId, customerId);
                ExampleConsole.done();
                return;
            }

            String subId = subscriptionId[0];

            ExampleHelpers.runExampleStep("\n=== 4. Get Subscription ===", () ->
                    ExampleHelpers.pretty(
                            "Fetched Subscription " + subId,
                            ExampleHelpers.execute(client, client.notificationChannels()
                                    .getSubscription(fiId, customerId, subId))),
                    "Get subscription");

            ExampleHelpers.runExampleStep("\n=== 5. Update Subscription ===", () -> {
                Subscription updatedSub = new Subscription();
                updatedSub.setId(subId);
                updatedSub.setFiId(fiId);
                updatedSub.setFiCustomerId(customerId);
                updatedSub.setEventTypeId(EVENT_BALANCE_THRESHOLD);
                updatedSub.setFulfillment("RECURRING");
                ExampleHelpers.pretty(
                        "Updated Subscription",
                        ExampleHelpers.execute(client, client.notificationChannels()
                                .updateSubscription(fiId, customerId, updatedSub)));
            }, "Update subscription");

            ExampleHelpers.runExampleStep("\n=== 6. Delete Subscription ===", () -> {
                ExampleHelpers.execute(client, client.notificationChannels().deleteSubscription(fiId, customerId, subId));
                ExampleConsole.println("  Subscription " + subId + " deleted successfully (204 No Content)");
            }, "Delete subscription");

            sendCustomerEvent(client, fiId, customerId);
        }
        ExampleConsole.done();
    }

    private static void sendCustomerEvent(CandescentClient client, String fiId, String customerId) {
        ExampleHelpers.runExampleStep("\n=== 7. Send Customer Event ===", () -> {
            Event event = new Event();
            event.setFiCustomerId(customerId);
            event.setFiId(fiId);
            event.setEventType(EVENT_BALANCE_THRESHOLD);
            Events payload = new Events();
            payload.setEvent(List.of(event));
            ExampleHelpers.execute(client, client.notificationChannels()
                    .sendCustomerEvent(fiId, customerId, List.of(payload)));
            ExampleConsole.println("  Customer event sent successfully (204 No Content)");
        }, "Send customer event");
    }
}
