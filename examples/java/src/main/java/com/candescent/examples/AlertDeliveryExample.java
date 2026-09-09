package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.errors.NotFoundException;
import com.candescent.di.generated.ApiException;
import com.candescent.di.generated.Pair;
import com.candescent.di.generated.api.HistoryAndEventsApi;
import com.candescent.di.generated.model.Event1;
import com.candescent.di.generated.model.EventDetails;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alert delivery: history listing and publishing realtime events.
 *
 * <p>OpenAPI spec: v1.8.0 — {@code publishEvents} body is {@link Event1}.
 *
 * <p>Prerequisites: {@code CANDESCENT_CLIENT_ID}, {@code CANDESCENT_CLIENT_SECRET},
 * {@code CANDESCENT_INSTITUTION_ID}. Optional: {@code CANDESCENT_HOST_USER_ID},
 * {@code CANDESCENT_LOGIN_ID}, {@code CANDESCENT_CUSTOMER_ID},
 * {@code CANDESCENT_ALERT_CONTACT_EMAIL}.
 */
public class AlertDeliveryExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");
        String institutionId = System.getenv("CANDESCENT_INSTITUTION_ID");
        String institutionCustomerId = System.getenv("CANDESCENT_CUSTOMER_ID");
        String contactEmail = ExampleHelpers.envOrDefault("CANDESCENT_ALERT_CONTACT_EMAIL", "demo@example.com");

        ExampleConsole.println("  HOST_USER_ID:            " + ExampleHelpers.envSetStatus(hostUserId));
        ExampleConsole.println("  LOGIN_ID:                " + ExampleHelpers.envSetStatus(loginId));
        ExampleConsole.println("  INSTITUTION_ID:          " + ExampleHelpers.envSetStatus(institutionId));
        ExampleConsole.println("  INSTITUTION_CUSTOMER_ID: " + ExampleHelpers.envSetStatus(institutionCustomerId));
        ExampleConsole.println("  ALERT_CONTACT_EMAIL:     " + ExampleHelpers.envSetStatus(contactEmail));
        ExampleConsole.println();

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised\n");

            ExampleHelpers.runExampleStep("=== Step 7: Get Alert History ===", () -> {
                try {
                    HistoryAndEventsApi.APIlistHistoryRequest builder =
                            client.historyAndEvents().listHistory();
                    applyUserIds(builder, hostUserId, loginId, institutionCustomerId);
                    ExampleHelpers.pretty("Alert History", ExampleHelpers.execute(client, builder));
                } catch (ApiException ex) {
                    var mapped = CandescentClient.mapException(ex);
                    if (mapped instanceof NotFoundException) {
                        ExampleConsole.println(
                                "  Skipping — no alert history records found for this user in staging");
                    } else {
                        throw mapped;
                    }
                }
            });

            ExampleHelpers.runExampleStep("\n=== Step 8: Publish Events ===", () -> {
                EventDetails details = new EventDetails()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("CUSTOM_SAVEUP_ALERT")
                        .eventSource("Candescent_" + (institutionId != null ? institutionId : "unknown"))
                        .additionalInfo(Map.of())
                        .institutionId(institutionId);
                if (institutionCustomerId != null && !institutionCustomerId.isBlank()) {
                    details.institutionCustomerId(institutionCustomerId);
                }

                Event1 event = new Event1()
                        .eventDomainType("notification")
                        .eventDetails(details);

                Map<String, Object> notification = Map.of("Name", "Candescent");
                Map<String, Object> destintionDetails = Map.of(
                        "contactChannels", List.of("EMAIL"),
                        "contactEmail", List.of(contactEmail));

                ExampleConsole.println("  Publishing realtime notification event:");
                ExampleConsole.println("    eventDomainType: notification");
                ExampleConsole.println("    eventType:       CUSTOM_SAVEUP_ALERT");
                ExampleConsole.println("    eventSource:     Candescent_"
                        + (institutionId != null ? institutionId : "unknown"));
                ExampleConsole.println("    contactEmail:    " + contactEmail);

                ExampleHelpers.postJsonVoid(
                        client,
                        "/v1/realtime-events",
                        ExampleHelpers.toRequestJson(
                                event,
                                Map.of(
                                        "notification", notification,
                                        "destintionDetails", destintionDetails)),
                        publishQueryParams(hostUserId, loginId),
                        publishHeaders(institutionCustomerId));
                ExampleConsole.println("\n  Event published successfully (202 Accepted)");
            });
        }
        ExampleConsole.done();
    }

    private static List<Pair> publishQueryParams(String hostUserId, String loginId) {
        List<Pair> queryParams = new ArrayList<>();
        if (hostUserId != null && !hostUserId.isBlank()) {
            queryParams.add(new Pair("hostUserId", hostUserId));
        } else if (loginId != null && !loginId.isBlank()) {
            queryParams.add(new Pair("loginId", loginId));
        }
        return queryParams;
    }

    private static Map<String, String> publishHeaders(String institutionCustomerId) {
        Map<String, String> headers = new HashMap<>();
        if (institutionCustomerId != null && !institutionCustomerId.isBlank()) {
            headers.put("institutionCustomerId", institutionCustomerId);
        }
        return headers;
    }

    private static void applyUserIds(
            HistoryAndEventsApi.APIlistHistoryRequest builder,
            String hostUserId,
            String loginId,
            String institutionCustomerId) {
        if (hostUserId != null && !hostUserId.isBlank()) {
            builder.hostUserId(hostUserId);
        } else if (loginId != null) {
            builder.loginId(loginId);
        }
        if (institutionCustomerId != null) {
            builder.institutionCustomerId(institutionCustomerId);
        }
    }
}
