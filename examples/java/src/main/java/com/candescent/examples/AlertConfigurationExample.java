package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.AlertTemplateResource;
import com.candescent.di.generated.model.AlertTypeResource;
import com.candescent.di.generated.model.ChannelContent;
import com.candescent.di.generated.model.InstitutionAlertTypeResource;

import java.util.List;
import java.util.Map;

/**
 * Alert configuration (steps 1–6):
 *   1. Get Alert Types
 *   2. Create Alert Type
 *   3. Get Alert Templates
 *   4. Create Alert Template  (conditional on alertTypeId from step 2)
 *   5. Get Institution Alert Types
 *   6. Create Institution Alert Type
 */
public class AlertConfigurationExample {

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String institutionId = ExampleHelpers.envOrDefault("CANDESCENT_INSTITUTION_ID", "05523");
        String alertTypeName = ExampleHelpers.envOrDefault(
                "CANDESCENT_ALERT_TYPE_NAME",
                "CUSTOM_DEVEX_ALERT_SDK_" + System.currentTimeMillis());

        ExampleConsole.println("  INSTITUTION_ID:  " + institutionId);
        ExampleConsole.println("  ALERT_TYPE_NAME: " + alertTypeName);
        ExampleConsole.println();

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised\n");

            // ── 1. Get Alert Types ──────────────────────────────────────────
            ExampleHelpers.runExampleStep("=== Step 1: Get Alert Types ===", () ->
                    ExampleHelpers.pretty("Alert Types", ExampleHelpers.execute(client, client.systemAlerts().listTypes())));

            // ── 2. Create Alert Type ────────────────────────────────────────
            final Long[] alertTypeId = {null};
            final String[] createdAlertTypeName = {alertTypeName};
            ExampleHelpers.runExampleStep("\n=== Step 2: Create Alert Type ===", () -> {
                AlertTypeResource resource = new AlertTypeResource();
                resource.setAlertTypeName(alertTypeName);
                resource.setEventTypeDomain("ACCOUNT");
                resource.setExternalSystem("DEVEX_REGISTRATION_" + institutionId);
                resource.setStatus(AlertTypeResource.StatusEnum.ACTIVE);
                resource.setDescription("Registration Notification");
                resource.setChannels("EMAIL");
                resource.setDisplayAlertTypeName("DevEx Portal Notification");
                resource.setAlertCategory("CUSTOM");
                resource.setInstitutionId(institutionId);
                var created = ExampleHelpers.postJson(
                        client,
                        "/v1/alert-types",
                        ExampleHelpers.toRequestJson(resource, Map.of("userInputType", "NA")),
                        AlertTypeResource.class);
                ExampleHelpers.pretty("Created Alert Type", created);
                if (created != null) {
                    alertTypeId[0] = created.getAlertTypeId();
                    if (created.getAlertTypeName() != null) {
                        createdAlertTypeName[0] = created.getAlertTypeName();
                    }
                    if (alertTypeId[0] != null) {
                        ExampleConsole.println("\n  alertTypeId:   " + alertTypeId[0]);
                        ExampleConsole.println("  alertTypeName: " + createdAlertTypeName[0]);
                    }
                }
            });

            // ── 3. Get Alert Templates ──────────────────────────────────────
            ExampleHelpers.runExampleStep("\n=== Step 3: Get Alert Templates ===", () ->
                    ExampleHelpers.pretty("Alert Templates", ExampleHelpers.execute(client, client.templates().listTemplates())));

            // ── 4. Create Alert Template (conditional on step 2 success) ───
            if (alertTypeId[0] == null) {
                ExampleConsole.println("\n=== Step 4: Create Alert Template ===");
                ExampleConsole.println("  Skipped — no alertTypeId from step 2");
            } else {
                final long typeId = alertTypeId[0];
                final String typeName = createdAlertTypeName[0];
                ExampleHelpers.runExampleStep("\n=== Step 4: Create Alert Template ===", () -> {
                    String emailBody =
                            "<html>\n<head>\n\t<title>Registration</title>\n</head>\n<body>\n"
                            + "<p>Hello<span th:remove=\"tag\" th:text=\"${NAME}\">${NAME}</span></p></body>\n</html>\n";
                    AlertTemplateResource tpl = new AlertTemplateResource();
                    tpl.setAlertTypeResourceId(typeId);
                    tpl.setAlertTypeName(typeName);
                    tpl.setInstitutionId(institutionId);
                    tpl.setState(AlertTemplateResource.StateEnum.DRAFT);
                    ChannelContent subject = new ChannelContent();
                    subject.setChannelType(ChannelContent.ChannelTypeEnum.EMAIL);
                    subject.setTemplateContentType(ChannelContent.TemplateContentTypeEnum.EMAIL_SUBJECT);
                    subject.setTemplateContent("Developer Portal Registration");
                    ChannelContent body = new ChannelContent();
                    body.setChannelType(ChannelContent.ChannelTypeEnum.EMAIL);
                    body.setTemplateContentType(ChannelContent.TemplateContentTypeEnum.EMAIL_BODY);
                    body.setTemplateContent(emailBody);
                    tpl.setTemplateContents(List.of(subject, body));
                    ExampleHelpers.pretty(
                            "Created Alert Template",
                            ExampleHelpers.postJson(
                                    client,
                                    "/v1/alert-templates",
                                    ExampleHelpers.toRequestJson(tpl, Map.of("vendor", "CANDESCENT")),
                                    AlertTemplateResource.class));
                });
            }

            // ── 5. Get Institution Alert Types ──────────────────────────────
            ExampleHelpers.runExampleStep("\n=== Step 5: Get Institution Alert Types ===", () ->
                    ExampleHelpers.pretty("Institution Alert Types",
                            ExampleHelpers.execute(client, client.institutionAlerts().listInstitutionTypes())));

            // ── 6. Create Institution Alert Type ────────────────────────────
            ExampleHelpers.runExampleStep("\n=== Step 6: Create Institution Alert Type ===", () -> {
                InstitutionAlertTypeResource instResource = new InstitutionAlertTypeResource();
                instResource.setAlertTypeName(createdAlertTypeName[0]);
                instResource.setChannelsOptd("EMAIL");
                instResource.setReason("Alerts when funds drop below a set threshold");
                instResource.setStatusOptd(InstitutionAlertTypeResource.StatusOptdEnum.ACTIVE);
                instResource.setInstitutionId(institutionId);
                ExampleHelpers.pretty("Created Institution Alert Type",
                        ExampleHelpers.execute(client, client.institutionAlerts().createInstitutionType(instResource)));
            });
        }
        ExampleConsole.done();
    }
}
