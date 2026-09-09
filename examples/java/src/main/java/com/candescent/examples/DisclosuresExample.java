package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.AccountType2;
import com.candescent.di.generated.model.InstitutionDisclosure;
import com.candescent.di.generated.model.InstitutionDisclosureCreateRequest;
import com.candescent.di.generated.model.InstitutionDisclosureUpdateRequest;
import com.candescent.di.generated.model.InstitutionUserDisclosureCreateRequest;
import com.candescent.di.generated.model.InstitutionUserDisclosureDeleteRequest;
import com.candescent.di.generated.model.InstitutionUserDisclosureStatus;
import com.candescent.di.generated.model.InstitutionUserDisclosureUpdateRequest;

/**
 * Disclosures: full CRUD lifecycle for institution-level and user-level disclosures.
 *
 * Steps:
 *   1. List Institution Disclosures
 *   2. Create Institution Disclosure
 *   3. Update Institution Disclosure  (conditional on step 2)
 *   4. List User Disclosures (hostUserId)
 *   5. Create User Disclosure (hostUserId)  (conditional on created disclosure ID)
 *   6. Update User Disclosure (hostUserId)  (conditional on step 5)
 *   7. Delete User Disclosure (hostUserId)  (conditional on CANDESCENT_ACCOUNT_ID)
 *   8. List User Disclosures (loginId)  (conditional on CANDESCENT_LOGIN_ID being set)
 */
public class DisclosuresExample {
    private static final String STEP_HEADING_SUFFIX = ") ===";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = ExampleHelpers.envOrNull("CANDESCENT_LOGIN_ID");
        String accountId = ExampleHelpers.envOrNull("CANDESCENT_ACCOUNT_ID");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised");

            // ── 1. List Institution Disclosures ─────────────────────────────
            ExampleHelpers.runExampleStep("\n=== 1. List Institution Disclosures ===", () ->
                    ExampleHelpers.pretty("Institution Disclosures",
                            ExampleHelpers.execute(client, client.institutionDisclosures().listInstitutionDisclosures())));

            // ── 2. Create Institution Disclosure ────────────────────────────
            final String[] createdDisclosureId = {null};
            final String[] disclosureName = {null};
            ExampleHelpers.runExampleStep("\n=== 2. Create Institution Disclosure ===", () -> {
                // Max 20 chars per DSC_12011; use short suffix based on millis mod 1000
                String name = "TEST_DISC_" + String.format("%03d", System.currentTimeMillis() % 1000);
                disclosureName[0] = name;
                InstitutionDisclosureCreateRequest req = new InstitutionDisclosureCreateRequest();
                req.setInstitutionDisclosureName(name);
                req.setInstitutionDisclosureStatus(true);
                InstitutionDisclosure result = ExampleHelpers.execute(client, client.institutionDisclosures()
                        .createInstitutionDisclosure(req));
                ExampleHelpers.pretty("Created Institution Disclosure", result);
                if (result != null && result.getInstitutionDisclosureId() != null) {
                    createdDisclosureId[0] = result.getInstitutionDisclosureId();
                    ExampleConsole.println("\n  Created disclosure ID: " + createdDisclosureId[0]);
                }
            });

            // ── 3. Update Institution Disclosure (conditional) ──────────────
            ExampleHelpers.runExampleStepWhen(
                    createdDisclosureId[0] != null,
                    "\n  Skipping update (no disclosure ID from create step)",
                    "\n=== 3. Update Institution Disclosure ===",
                    () -> {
                        String updatedName = "TEST_DISC_" + String.format("%03d", (System.currentTimeMillis() + 1) % 1000);
                        InstitutionDisclosureUpdateRequest upd = new InstitutionDisclosureUpdateRequest();
                        upd.setInstitutionDisclosureId(createdDisclosureId[0]);
                        upd.setInstitutionDisclosureName(updatedName);
                        upd.setInstitutionDisclosureStatus(true);
                        ExampleHelpers.pretty("Updated Institution Disclosure",
                                ExampleHelpers.execute(client, client.institutionDisclosures()
                                        .updateInstitutionDisclosure(createdDisclosureId[0], upd)));
                    });

            // ── User Disclosure flow — hostUserId path (steps 4–7) ──────────
            runUserDisclosureFlow(client, "hostUserId", hostUserId, null,
                    createdDisclosureId[0], disclosureName[0], accountId);

            // ── 8. List User Disclosures (loginId) — list only, no duplicate CRUD
            if (loginId != null && !loginId.isBlank()) {
                printUserDisclosureHeader("loginId");
                listUserDisclosures(client, "loginId", null, loginId);
            } else {
                ExampleConsole.println("\n  Skipping loginId path (CANDESCENT_LOGIN_ID not set)");
            }

            if (accountId == null) {
                ExampleConsole.println("\n  Note: CANDESCENT_ACCOUNT_ID not set — delete step skipped");
            }
        }
        ExampleConsole.done();
    }

    private static void runUserDisclosureFlow(
            CandescentClient client,
            String label,
            String hostUserId,
            String loginId,
            String createdDisclosureId,
            String disclosureName,
            String accountId) {

        printUserDisclosureHeader(label);
        listUserDisclosures(client, label, hostUserId, loginId);

        if (createdDisclosureId == null) {
            ExampleConsole.println("  Skipping user disclosure create/update/delete (no institution disclosure ID)");
            return;
        }

        boolean userDisclosureCreated = createUserDisclosure(
                client, label, hostUserId, loginId, createdDisclosureId);
        if (userDisclosureCreated) {
            updateUserDisclosure(client, label, hostUserId, loginId, createdDisclosureId, disclosureName);
        }
        deleteUserDisclosureIfPossible(client, label, hostUserId, loginId, accountId);
    }

    private static void printUserDisclosureHeader(String label) {
        ExampleConsole.println("\n" + "=".repeat(72));
        ExampleConsole.println("  User Disclosure Flow — " + label);
        ExampleConsole.println("=".repeat(72));
    }

    private static void listUserDisclosures(
            CandescentClient client, String label, String hostUserId, String loginId) {
        ExampleHelpers.runExampleStep("\n=== List User Disclosures (" + label + STEP_HEADING_SUFFIX, () -> {
            var req = client.userDisclosures().listUserDisclosures();
            if (hostUserId != null) {
                req.hostUserId(hostUserId);
            }
            if (loginId != null) {
                req.loginId(loginId);
            }
            ExampleHelpers.pretty("User Disclosures (" + label + ")", ExampleHelpers.execute(client, req));
        });
    }

    private static boolean createUserDisclosure(
            CandescentClient client,
            String label,
            String hostUserId,
            String loginId,
            String createdDisclosureId) {
        final boolean[] created = {false};
        ExampleHelpers.runExampleStep("\n=== Create User Disclosure (" + label + STEP_HEADING_SUFFIX, () -> {
            InstitutionUserDisclosureCreateRequest req = new InstitutionUserDisclosureCreateRequest();
            req.setInstitutionDisclosureId(createdDisclosureId);
            req.setInstitutionUserDisclosureStatus(InstitutionUserDisclosureStatus.ACCEPTED);
            var builder = client.userDisclosures().createUserDisclosure(req);
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            }
            if (loginId != null) {
                builder.loginId(loginId);
            }
            ExampleHelpers.execute(client, builder);
            ExampleConsole.println("  User disclosure created successfully");
            created[0] = true;
        });
        return created[0];
    }

    private static void updateUserDisclosure(
            CandescentClient client,
            String label,
            String hostUserId,
            String loginId,
            String createdDisclosureId,
            String disclosureName) {
        ExampleHelpers.runExampleStep("\n=== Update User Disclosure (" + label + STEP_HEADING_SUFFIX, () -> {
            InstitutionUserDisclosureUpdateRequest upd = new InstitutionUserDisclosureUpdateRequest();
            upd.setInstitutionDisclosureId(createdDisclosureId);
            upd.setInstitutionDisclosureName(disclosureName);
            upd.setInstitutionUserDisclosureStatus(InstitutionUserDisclosureStatus.NOT_ACCEPTED);
            var builder = client.userDisclosures().updateUserDisclosure(upd);
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            }
            if (loginId != null) {
                builder.loginId(loginId);
            }
            ExampleHelpers.execute(client, builder);
            ExampleHelpers.pretty("Updated User Disclosure (" + label + ")", upd);
        });
    }

    private static void deleteUserDisclosureIfPossible(
            CandescentClient client,
            String label,
            String hostUserId,
            String loginId,
            String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            ExampleHelpers.runExampleStep("\n=== Delete User Disclosure (" + label + STEP_HEADING_SUFFIX, () -> {
                InstitutionUserDisclosureDeleteRequest del = new InstitutionUserDisclosureDeleteRequest();
                del.setInstitutionDisclosureName("OLS");
                del.setAccountId(accountId);
                var builder = client.userDisclosures().deleteUserDisclosure(AccountType2.CHECKING, del);
                if (hostUserId != null) {
                    builder.hostUserId(hostUserId);
                }
                if (loginId != null) {
                    builder.loginId(loginId);
                }
                ExampleHelpers.execute(client, builder);
                ExampleConsole.println("  User disclosure deleted successfully");
            });
        } else {
            ExampleConsole.println("\n  Skipping delete (" + label + ") — CANDESCENT_ACCOUNT_ID required");
        }
    }
}
