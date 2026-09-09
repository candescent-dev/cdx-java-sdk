package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.ApiException;
import com.candescent.di.generated.model.CurrencyCode2;
import com.candescent.di.generated.model.DIAccountType3;
import com.candescent.di.generated.model.Money3;
import com.candescent.di.generated.model.Recipient;
import com.candescent.di.generated.model.Recipients;
import com.candescent.di.generated.model.Transfer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Money movement: recipient CRUD and transfers (mirrors examples/typescript/money-movement.ts).
 *
 * <p>Stage defaults align with apigee-recipients-stg Postman environment (nongeneric provider).
 */
public class MoneyMovementExample {
    private static final String DEFAULT_PROVIDER_TYPE = "nongeneric";
    private static final String DEFAULT_SENDER_ACCOUNT_NUMBER = "0003";
    private static final String DEFAULT_RECIPIENT_ACCOUNT_NUMBER = "0099";
    private static final String DEFAULT_RECIPIENT_PASSCODE = "SIM";
    private static final String DEFAULT_RECIPIENT_EMAIL = "postman.recipient@example.com";
    private static final String DEFAULT_RECIPIENT_MEMBER_NUMBER = "applepa";
    private static final String DEFAULT_RECIPIENT_ACCOUNT_TYPE = "CHECKING";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();

        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = ExampleHelpers.envOrNull("CANDESCENT_LOGIN_ID");
        String accountId = ExampleHelpers.envOrNull("CANDESCENT_ACCOUNT_ID");
        String fallbackRecipientId = ExampleHelpers.envOrNull("FALLBACK_RECIPIENT_ID");
        String senderAccountNumber = ExampleHelpers.envOrDefault(
                "CANDESCENT_SENDER_ACCOUNT_NUMBER", DEFAULT_SENDER_ACCOUNT_NUMBER);
        boolean useHostUserId = hostUserId != null && !hostUserId.isBlank();

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised\n");

            String fromAccountId = ExampleHelpers.resolveAccountId(
                    client, hostUserId, loginId, accountId, senderAccountNumber);

            final String[] listedRecipientId = {null};
            ExampleHelpers.runExampleStep(
                    "Step 1 — List Recipients  [GET /db-recipients/v1/recipients]",
                    () -> {
                        Recipients response = useHostUserId
                                ? ExampleHelpers.execute(client, client.recipients().listRecipients().hostUserId(hostUserId))
                                : ExampleHelpers.execute(client, client.recipients().listRecipients().loginId(loginId));
                        ExampleHelpers.pretty("List Recipients Response", response);
                        listedRecipientId[0] = firstRecipientId(response);
                        if (listedRecipientId[0] == null) {
                            listedRecipientId[0] = discoverRecipientId(client, useHostUserId, hostUserId, loginId);
                        }
                    },
                    "List Recipients");

            RecipientCreateResult createResult = createRecipientStep(
                    client, hostUserId, loginId, fallbackRecipientId, fromAccountId, useHostUserId, listedRecipientId[0]);

            ExampleHelpers.runExampleStepWhen(
                    createResult.created(),
                    "\n  Skipping get recipient (create step did not return a recipient ID).",
                    "\nStep 3 — Get Recipient  [GET /db-recipients/v1/recipients/{recipientId}]",
                    () -> {
                        Recipients response = useHostUserId
                                ? ExampleHelpers.execute(client, client.recipients()
                                        .getRecipient(createResult.recipientId())
                                        .hostUserId(hostUserId))
                                : ExampleHelpers.execute(client, client.recipients()
                                        .getRecipient(createResult.recipientId())
                                        .loginId(loginId));
                        ExampleHelpers.pretty("Get Recipient Response", response);
                    },
                    "Get Recipient");

            ExampleHelpers.runExampleStepWhen(
                    createResult.created(),
                    "\n  Skipping update recipient (create step did not return a recipient ID).",
                    "\nStep 4 — Update Recipient  [PUT /db-recipients/v1/recipients/{recipientId}]",
                    () -> {
                        Recipients response = useHostUserId
                                ? ExampleHelpers.execute(client, client.recipients()
                                        .updateRecipient(
                                                createResult.recipientId(),
                                                updatedRecipient(createResult.recipientId()))
                                        .hostUserId(hostUserId)
                                        .fromAccountId(fromAccountId))
                                : ExampleHelpers.execute(client, client.recipients()
                                        .updateRecipient(
                                                createResult.recipientId(),
                                                updatedRecipient(createResult.recipientId()))
                                        .loginId(loginId)
                                        .fromAccountId(fromAccountId));
                        ExampleHelpers.pretty("Update Recipient Response", response);
                    },
                    "Update Recipient");

            ExampleHelpers.runExampleStepWhen(
                    createResult.created(),
                    "\n  Skipping delete recipient (create step did not return a recipient ID).",
                    "\nStep 5 — Delete Recipient  [DELETE /db-recipients/v1/recipients/{recipientId}]",
                    () -> {
                        Recipients response = useHostUserId
                                ? ExampleHelpers.execute(client, client.recipients()
                                        .deleteRecipient(createResult.recipientId())
                                        .hostUserId(hostUserId))
                                : ExampleHelpers.execute(client, client.recipients()
                                        .deleteRecipient(createResult.recipientId())
                                        .loginId(loginId));
                        ExampleHelpers.pretty("Delete Recipient Response", response);
                    },
                    "Delete Recipient");

            ExampleHelpers.runExampleStepWhen(
                    accountId != null,
                    "  Skipping — CANDESCENT_ACCOUNT_ID not set.",
                    "\nStep 6 — Create Transfer  [POST /db-transfers/v1/transfers]",
                    () -> {
                        Transfer transfer = new Transfer();
                        transfer.setFromAccountId(accountId);
                        transfer.setToAccountId("destination-account-id");
                        transfer.setMemo("SDK example transfer");
                        Money3 amount = new Money3();
                        amount.setCurrencyCode(CurrencyCode2.USD);
                        amount.setAmount(BigDecimal.TEN);
                        transfer.setAmount(amount);
                        Transfer response = useHostUserId
                                ? ExampleHelpers.execute(client, client.transfers()
                                        .createTransfer()
                                        .transferRequest(transfer)
                                        .hostUserId(hostUserId))
                                : ExampleHelpers.execute(client, client.transfers()
                                        .createTransfer()
                                        .transferRequest(transfer)
                                        .loginId(loginId));
                        ExampleHelpers.pretty("Create Transfer Response", response);
                    },
                    "Create Transfer");
        }
        ExampleConsole.done();
    }

    private static RecipientCreateResult createRecipientStep(
            CandescentClient client,
            String hostUserId,
            String loginId,
            String fallbackRecipientId,
            String fromAccountId,
            boolean useHostUserId,
            String existingRecipientId) {
        if (existingRecipientId != null) {
            ExampleConsole.println("\n  Using existing recipient from list: " + existingRecipientId);
            return new RecipientCreateResult(existingRecipientId, true);
        }

        final String[] recipientId = {null};
        final boolean[] created = {false};
        ExampleHelpers.runExampleStep(
                "\nStep 2 — Create Recipient  [POST /db-recipients/v1/recipients]",
                () -> attemptCreateRecipient(
                        client, hostUserId, loginId, fromAccountId, useHostUserId, recipientId, created),
                "Create Recipient");

        return resolveRecipientCreateResult(recipientId[0], created[0], fallbackRecipientId);
    }

    private static void attemptCreateRecipient(
            CandescentClient client,
            String hostUserId,
            String loginId,
            String fromAccountId,
            boolean useHostUserId,
            String[] recipientId,
            boolean[] created)
            throws Exception {
        try {
            Recipients response = executeCreateRecipient(
                    client, hostUserId, loginId, fromAccountId, useHostUserId);
            ExampleHelpers.pretty("Create Recipient Response", response);
            recipientId[0] = firstRecipientId(response);
            if (recipientId[0] != null) {
                created[0] = true;
                ExampleConsole.println("\n  -> Recipient ID: " + recipientId[0]);
            }
        } catch (ApiException ex) {
            if (tryRecoverExistingRecipient(client, useHostUserId, hostUserId, loginId, ex, recipientId, created)) {
                return;
            }
            throw ex;
        }
    }

    private static Recipients executeCreateRecipient(
            CandescentClient client,
            String hostUserId,
            String loginId,
            String fromAccountId,
            boolean useHostUserId)
            throws ApiException {
        return useHostUserId
                ? ExampleHelpers.execute(client, client.recipients()
                        .createRecipient(newRecipient())
                        .hostUserId(hostUserId)
                        .fromAccountId(fromAccountId))
                : ExampleHelpers.execute(client, client.recipients()
                        .createRecipient(newRecipient())
                        .loginId(loginId)
                        .fromAccountId(fromAccountId));
    }

    private static boolean tryRecoverExistingRecipient(
            CandescentClient client,
            boolean useHostUserId,
            String hostUserId,
            String loginId,
            ApiException ex,
            String[] recipientId,
            boolean[] created)
            throws Exception {
        String body = ex.getResponseBody();
        if (body == null
                || (!body.contains("REC_13001")
                        && !body.contains("REC_13002")
                        && !body.contains("REC_12011"))) {
            return false;
        }
        recipientId[0] = discoverRecipientId(client, useHostUserId, hostUserId, loginId);
        if (recipientId[0] == null) {
            return false;
        }
        created[0] = true;
        ExampleConsole.println("\n  Recipient already exists — reusing ID: " + recipientId[0]);
        return true;
    }

    private static RecipientCreateResult resolveRecipientCreateResult(
            String recipientId, boolean created, String fallbackRecipientId) {
        if (created) {
            return new RecipientCreateResult(recipientId, true);
        }
        if (fallbackRecipientId != null) {
            ExampleConsole.println("  Using FALLBACK_RECIPIENT_ID: " + fallbackRecipientId);
            return new RecipientCreateResult(fallbackRecipientId, false);
        }
        ExampleConsole.println("  No recipient ID available — skipping dependent recipient steps.");
        ExampleConsole.println("  Set FALLBACK_RECIPIENT_ID to exercise get/update/delete with a known ID.");
        return new RecipientCreateResult(null, false);
    }

    private static Recipient newRecipient() {
        String providerType = ExampleHelpers.envOrDefault("RECIPIENT_PROVIDER_TYPE", DEFAULT_PROVIDER_TYPE)
                .toLowerCase(Locale.ROOT);
        Recipient recipient = new Recipient();
        recipient.setAccountNumber(ExampleHelpers.envOrDefault(
                "RECIPIENT_ACCOUNT_NUMBER", DEFAULT_RECIPIENT_ACCOUNT_NUMBER));
        recipient.setPassCode(ExampleHelpers.envOrDefault("RECIPIENT_PASSCODE", DEFAULT_RECIPIENT_PASSCODE));
        recipient.setNickName("SDK_Rcpt_" + System.currentTimeMillis());
        recipient.setEmail(ExampleHelpers.envOrDefault("RECIPIENT_EMAIL", DEFAULT_RECIPIENT_EMAIL));
        if ("generic".equals(providerType)) {
            recipient.setMemberNumber(ExampleHelpers.envOrDefault(
                    "RECIPIENT_MEMBER_NUMBER", DEFAULT_RECIPIENT_MEMBER_NUMBER));
            recipient.setAccountType(parseAccountType(ExampleHelpers.envOrDefault(
                    "RECIPIENT_ACCOUNT_TYPE", DEFAULT_RECIPIENT_ACCOUNT_TYPE)));
        }
        return recipient;
    }

    private static Recipient updatedRecipient(String recipientId) {
        Recipient recipient = new Recipient();
        recipient.setId(recipientId);
        recipient.setNickName(ExampleHelpers.envOrDefault(
                "RECIPIENT_NICKNAME_UPDATED", "PmTestRcptUpd"));
        recipient.setEmail(ExampleHelpers.envOrDefault("RECIPIENT_EMAIL", DEFAULT_RECIPIENT_EMAIL));
        return recipient;
    }

    private static DIAccountType3 parseAccountType(String value) {
        try {
            return DIAccountType3.fromValue(value);
        } catch (IllegalArgumentException ex) {
            return DIAccountType3.CHECKING;
        }
    }

    private static String discoverRecipientId(
            CandescentClient client, boolean useHostUserId, String hostUserId, String loginId)
            throws Exception {
        String raw = useHostUserId
                ? ExampleHelpers.executeRaw(client, client.recipients().listRecipients().hostUserId(hostUserId))
                : ExampleHelpers.executeRaw(client, client.recipients().listRecipients().loginId(loginId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonElement tree = JsonParser.parseString(raw);
        if (!tree.isJsonObject()) {
            return null;
        }
        JsonObject obj = tree.getAsJsonObject();
        JsonArray recipients = null;
        if (obj.has(ExampleHelpers.JSON_RECIPIENTS) && obj.get(ExampleHelpers.JSON_RECIPIENTS).isJsonArray()) {
            recipients = obj.getAsJsonArray(ExampleHelpers.JSON_RECIPIENTS);
        } else if (obj.has(ExampleHelpers.JSON_RECIPIENTS_PASCAL)
                && obj.get(ExampleHelpers.JSON_RECIPIENTS_PASCAL).isJsonArray()) {
            recipients = obj.getAsJsonArray(ExampleHelpers.JSON_RECIPIENTS_PASCAL);
        }
        if (recipients != null && !recipients.isEmpty() && recipients.get(0).isJsonObject()) {
            JsonElement id = recipients.get(0).getAsJsonObject().get("id");
            return id != null && !id.isJsonNull() ? id.getAsString() : null;
        }
        return null;
    }

    private static String firstRecipientId(Recipients response) {
        if (response == null || response.getRecipients() == null || response.getRecipients().isEmpty()) {
            return null;
        }
        return response.getRecipients().get(0).getId();
    }

    private record RecipientCreateResult(String recipientId, boolean created) {}
}
