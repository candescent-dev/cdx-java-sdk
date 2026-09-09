package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.AlertPreferenceAccountDetailsModel;
import com.candescent.di.generated.model.AlertPreferenceDetailsModel;
import com.candescent.di.generated.model.AlertPreferenceResource;
import com.candescent.di.generated.model.AlertPreferenceResources;

import java.util.HashMap;
import java.util.List;

/**
 * Alert preferences: list and create user alert preferences.
 */
public class AlertPreferencesExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");
        String institutionCustomerId = ExampleHelpers.envOrNull("CANDESCENT_CUSTOMER_ID");
        String institutionId = ExampleHelpers.envOrDefault("CANDESCENT_INSTITUTION_ID", "05523");
        String accountId = ExampleHelpers.envOrNull("CANDESCENT_ACCOUNT_ID");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            final AlertPreferenceResources[] listed = {null};
            ExampleHelpers.runExampleStep("=== Step 9: Get User Alert Preferences ===", () -> {
                var builder = client.userPreferences().callList();
                if (institutionCustomerId != null && !institutionCustomerId.isBlank()) {
                    builder.institutionCustomerId(institutionCustomerId);
                }
                if (hostUserId != null && !hostUserId.isBlank()) {
                    builder.hostUserId(hostUserId);
                } else if (loginId != null) {
                    builder.loginId(loginId);
                }
                listed[0] = ExampleHelpers.execute(client, builder);
                ExampleHelpers.pretty("User Alert Preferences", listed[0]);
            });

            ExampleHelpers.runExampleStep("\n=== Step 10: Create User Alert Preferences ===", () -> {
                if (institutionCustomerId == null || institutionCustomerId.isBlank()) {
                    ExampleConsole.println("  Skipping create — set CANDESCENT_CUSTOMER_ID");
                    return;
                }
                AlertPreferenceResource pref = buildCreatePreference(
                        listed[0], institutionCustomerId, institutionId, accountId);
                var builder = client.userPreferences().create(pref).institutionCustomerId(institutionCustomerId);
                if (hostUserId != null && !hostUserId.isBlank()) {
                    builder.hostUserId(hostUserId);
                } else if (loginId != null) {
                    builder.loginId(loginId);
                }
                ExampleHelpers.pretty("Created Preference", ExampleHelpers.execute(client, builder));
            });
        }
        ExampleConsole.done();
    }

    private static AlertPreferenceResource buildCreatePreference(
            AlertPreferenceResources listed,
            String institutionCustomerId,
            String institutionId,
            String accountId) {
        List<AlertPreferenceResource> existing =
                listed != null ? listed.getAlertPreferences() : null;
        if (existing != null && !existing.isEmpty()) {
            return clonePreferenceForCreate(existing.get(0));
        }

        AlertPreferenceResource pref = new AlertPreferenceResource();
        pref.setAdditionalInfo(new HashMap<String, Object>());
        pref.setAlertOpted(true);
        pref.setAllowCallback(true);
        pref.setDefaultPreferences(true);

        AlertPreferenceAccountDetailsModel accountDetails = new AlertPreferenceAccountDetailsModel();
        accountDetails.setAccountId(accountId != null ? accountId : "9MweppliRXyP-9G4Dzmfcz8-1Lnfbhi_W0kotSR9UnM");
        accountDetails.setAccountExternalId("ctc-vd-a6a54172-755d-4c0b-b1a4-42f1bea73c1c");
        accountDetails.setCardNumber("4471");
        pref.setAlertPreferenceAccountDetails(accountDetails);

        AlertPreferenceDetailsModel details = new AlertPreferenceDetailsModel();
        details.setAlertPrefId(0L);
        details.setAlertTypeName("VISA-HOUSEHOLD_SPEND_ALERTS");
        details.setChannelTypeName("EMAIL");
        details.setExternalId("string");
        details.setInstitutionCustomerId(institutionCustomerId);
        details.setInstitutionId(institutionId);
        pref.setAlertPreferenceDetails(details);
        return pref;
    }

    private static AlertPreferenceResource clonePreferenceForCreate(AlertPreferenceResource source) {
        AlertPreferenceResource pref = new AlertPreferenceResource();
        pref.setAdditionalInfo(source.getAdditionalInfo() != null
                ? source.getAdditionalInfo()
                : new HashMap<String, Object>());
        pref.setAlertOpted(true);
        pref.setAllowCallback(source.getAllowCallback() == null || source.getAllowCallback());
        pref.setDefaultPreferences(
                source.getDefaultPreferences() != null && source.getDefaultPreferences());

        if (source.getAlertPreferenceAccountDetails() != null) {
            AlertPreferenceAccountDetailsModel account = source.getAlertPreferenceAccountDetails();
            AlertPreferenceAccountDetailsModel accountDetails = new AlertPreferenceAccountDetailsModel();
            accountDetails.setAccountId(account.getAccountId());
            accountDetails.setAccountExternalId(account.getAccountExternalId());
            accountDetails.setCardNumber(account.getCardNumber());
            pref.setAlertPreferenceAccountDetails(accountDetails);
        }

        if (source.getAlertPreferenceDetails() != null) {
            AlertPreferenceDetailsModel details = source.getAlertPreferenceDetails();
            AlertPreferenceDetailsModel cloned = new AlertPreferenceDetailsModel();
            cloned.setAlertPrefId(0L);
            cloned.setAlertTypeName(details.getAlertTypeName());
            cloned.setChannelTypeName(details.getChannelTypeName());
            cloned.setExternalId(details.getExternalId());
            cloned.setInstitutionCustomerId(details.getInstitutionCustomerId());
            cloned.setInstitutionId(details.getInstitutionId());
            pref.setAlertPreferenceDetails(cloned);
        }
        return pref;
    }
}
