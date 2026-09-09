package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.BbRegistration;
import com.candescent.di.generated.model.BbRegistrationUser;
import com.candescent.di.generated.model.BbRegistrationUserType;
import com.candescent.di.generated.model.BusinessAddress;
import com.candescent.di.generated.model.BusinessContact;
import com.candescent.di.generated.model.BusinessTinInfo;

import java.util.List;

/**
 * Business Banking Registration:
 *   1.  Get Registration Config
 *   2.  Create Business Registration
 *   3.  Lookup by Confirmation Number  (conditional on step 2)
 *   4.  Lookup by Registration ID      (conditional on step 2)
 *   5.  Business Details — LOGIN_ID    (conditional on CANDESCENT_BB_LOGIN_ID)
 *   5b. Business Details — LOGIN_ID + includeUsers
 *   6.  Business Details — BUSINESS_ID (conditional on CANDESCENT_BB_BUSINESS_ID)
 *   6b. Business Details — BUSINESS_ID + includeTins
 *   6c. Business Details — BUSINESS_ID + includeUsers + includeTins
 *   7.  Negative: empty businessName (expect 400)
 *   8.  Negative: invalid TIN
 *   9.  Negative: invalid confirmation number
 *  10.  Negative: invalid searchType
 */
public class BusinessRegistrationExample {
    private static final String SEARCH_BY_LOGIN_ID = "LOGIN_ID";
    private static final String SEARCH_BY_BUSINESS_ID = "BUSINESS_ID";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String bbLoginId    = ExampleHelpers.envOrNull("CANDESCENT_BB_LOGIN_ID");
        String bbBusinessId = ExampleHelpers.envOrNull("CANDESCENT_BB_BUSINESS_ID");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised (Business Banking)\n");

            // ── 1. Get Registration Config ───────────────────────────────────
            ExampleHelpers.runExampleStep("\n=== 1. Get Registration Config ===", () ->
                    ExampleHelpers.pretty("Registration Config",
                            ExampleHelpers.execute(client, client.registration().getRegistrationConfig())));

            // ── 2. Create Business Registration ─────────────────────────────
            final String[] confirmationNumber = {null};
            final String[] registrationId     = {null};
            ExampleHelpers.runExampleStep("\n=== 2. Create Business Registration ===", () -> {
                BbRegistration result = ExampleHelpers.execute(client, client.registration()
                        .createRegistration(buildValidRegistration()));
                ExampleHelpers.pretty("Registration Result", result);
                if (result != null) {
                    confirmationNumber[0] = result.getConfirmationNumber();
                    registrationId[0]     = result.getId();
                    if (confirmationNumber[0] != null)
                        ExampleConsole.println("\n  Confirmation number: " + confirmationNumber[0]);
                    if (registrationId[0] != null)
                        ExampleConsole.println("  Registration ID: " + registrationId[0]);
                }
            });

            // ── 3. Lookup by Confirmation Number ─────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    confirmationNumber[0] != null,
                    "\n  Skipping lookup (no confirmation number from create step)",
                    "\n=== 3. Lookup by Confirmation Number ===",
                    () -> ExampleHelpers.pretty("Registration (by confirmation)",
                            ExampleHelpers.execute(client, client.registration()
                                    .getRegistrationByConfirmation(confirmationNumber[0]))));

            // ── 4. Lookup by Registration ID ─────────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    registrationId[0] != null,
                    "\n  Skipping lookup (no registration ID from create step)",
                    "\n=== 4. Lookup by Registration ID ===",
                    () -> ExampleHelpers.pretty("Registration (by ID)",
                            ExampleHelpers.execute(client, client.registration()
                                    .getRegistrationById(registrationId[0]))));

            // ── 5/5b. Business Details by LOGIN_ID ───────────────────────────
            if (bbLoginId != null && !bbLoginId.isBlank()) {
                ExampleHelpers.runExampleStep("\n=== 5. Business Details — LOGIN_ID ===", () ->
                        ExampleHelpers.pretty("Business Details (LOGIN_ID)",
                                ExampleHelpers.execute(client, client.profile().getBusinessDetails(SEARCH_BY_LOGIN_ID, bbLoginId))));

                ExampleHelpers.runExampleStep("\n=== 5b. Business Details — LOGIN_ID + includeUsers ===", () ->
                        ExampleHelpers.pretty("Business Details (LOGIN_ID + users)",
                                ExampleHelpers.execute(client, client.profile().getBusinessDetails(SEARCH_BY_LOGIN_ID, bbLoginId)
                                        .includeUsers(true))));
            } else {
                ExampleConsole.println("\n  Skipping LOGIN_ID lookup (CANDESCENT_BB_LOGIN_ID not set)");
            }

            // ── 6/6b/6c. Business Details by BUSINESS_ID ────────────────────
            if (bbBusinessId != null && !bbBusinessId.isBlank()) {
                ExampleHelpers.runExampleStep("\n=== 6. Business Details — BUSINESS_ID ===", () ->
                        ExampleHelpers.pretty("Business Details (BUSINESS_ID)",
                                ExampleHelpers.execute(client, client.profile().getBusinessDetails(SEARCH_BY_BUSINESS_ID, bbBusinessId))));

                ExampleHelpers.runExampleStep("\n=== 6b. Business Details — BUSINESS_ID + includeTins ===", () ->
                        ExampleHelpers.pretty("Business Details (BUSINESS_ID + TINs)",
                                ExampleHelpers.execute(client, client.profile().getBusinessDetails(SEARCH_BY_BUSINESS_ID, bbBusinessId)
                                        .includeTins(true))));

                ExampleHelpers.runExampleStep("\n=== 6c. Business Details — BUSINESS_ID + includeUsers + includeTins ===", () ->
                        ExampleHelpers.pretty("Business Details (BUSINESS_ID + users + TINs)",
                                ExampleHelpers.execute(client, client.profile().getBusinessDetails(SEARCH_BY_BUSINESS_ID, bbBusinessId)
                                        .includeUsers(true).includeTins(true))));
            } else {
                ExampleConsole.println("\n  Skipping BUSINESS_ID lookup (CANDESCENT_BB_BUSINESS_ID not set)");
            }

            // ── 7. Negative: empty businessName ─────────────────────────────
            ExampleHelpers.runExpectedErrorStep("\n=== 7. Negative: Empty businessName (expect 400) ===", () -> {
                BbRegistration bad = buildValidRegistration();
                bad.setBusinessName("");
                ExampleHelpers.execute(client, client.registration().createRegistration(bad));
            });

            // ── 8. Negative: invalid TIN ─────────────────────────────────────
            ExampleHelpers.runExpectedErrorStep("\n=== 8. Negative: Invalid TIN (expect validation error) ===", () -> {
                BbRegistration bad = buildValidRegistration();
                BusinessTinInfo badTin = new BusinessTinInfo();
                badTin.setTinNumber("215");
                badTin.setTinName("Bad TIN");
                badTin.setPrimary(true);
                bad.setTins(List.of(badTin));
                ExampleHelpers.execute(client, client.registration().createRegistration(bad));
            });

            // ── 9. Negative: invalid confirmation number ─────────────────────
            ExampleHelpers.runExpectedErrorStep("\n=== 9. Negative: Invalid confirmation number (expect error) ===", () ->
                    ExampleHelpers.execute(client, client.registration().getRegistrationByConfirmation("INVALID-CONF-000")));

            // ── 10. Negative: invalid searchType ─────────────────────────────
            ExampleHelpers.runExpectedErrorStep("\n=== 10. Negative: Invalid searchType (expect error) ===", () ->
                    ExampleHelpers.execute(client, client.profile().getBusinessDetails("INVALID_TYPE", "test")));
        }
        ExampleConsole.done();
    }

    private static BbRegistration buildValidRegistration() {
        BusinessAddress address = new BusinessAddress();
        address.setAddress1("100 Main Street");
        address.setCity("Springfield");
        address.setState("NJ");
        address.setZipCode("07001");

        BusinessContact contact = new BusinessContact();
        contact.setFirstName("John");
        contact.setLastName("Doe");
        contact.setEmail("sdk-test@example.com");
        contact.setPhoneNumber("555-123-4567");
        contact.setAddress(address);

        BbRegistrationUser primaryAdmin = new BbRegistrationUser();
        primaryAdmin.setFirstName("John");
        primaryAdmin.setLastName("Doe");
        primaryAdmin.setEmail("sdk-admin@example.com");
        primaryAdmin.setPhoneNumber("5551234567");
        primaryAdmin.setUserType(BbRegistrationUserType.PRIMARY_ADMIN);

        BbRegistrationUser secondaryAdmin = new BbRegistrationUser();
        secondaryAdmin.setFirstName("Jane");
        secondaryAdmin.setLastName("Smith");
        secondaryAdmin.setEmail("sdk-admin2@example.com");
        secondaryAdmin.setPhoneNumber("5559876543");
        secondaryAdmin.setUserType(BbRegistrationUserType.SECONDARY_ADMIN);

        BusinessTinInfo tin = new BusinessTinInfo();
        tin.setTinNumber("215732250");
        tin.setTinName("SDK Test Business");
        tin.setMemberNumber("123456");
        tin.setHostPassword("TestPass1");
        tin.setPrimary(true);

        BbRegistration reg = new BbRegistration();
        reg.setBusinessName("SDK-Test-Registration");
        reg.setContact(contact);
        reg.setUsers(List.of(primaryAdmin, secondaryAdmin));
        reg.setTins(List.of(tin));
        reg.setOnlineFeatures(List.of("ACH Origination", "ACH Positive Pay"));
        reg.setAdditionalServices(List.of("Card Services"));
        return reg;
    }
}
