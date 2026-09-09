package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.errors.NotFoundException;
import com.candescent.di.generated.model.ContactInfo;
import com.candescent.di.generated.model.ContactInfoEmail;
import com.candescent.di.generated.model.Destination;
import com.candescent.di.generated.model.ContactMethodResponse;
import com.candescent.di.generated.model.ResetPasswordRequest;
import com.candescent.di.generated.model.UserStatus1;

/**
 * Customer Management: contact methods, update contact info, reset password, unlock user.
 * Mirrors examples/typescript/customer-management.ts.
 */
public class CustomerManagementExample {
    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String customerId = ExampleHelpers.envOrDefault(
                "CANDESCENT_CUSTOMER_ID", "00000000-0000-0000-0000-000000000000");

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised");
            ExampleConsole.println("  customerId: " + customerId);

            final String[] contactMethodId = {null};
            ExampleHelpers.runExampleStep("\n=== Step 1: Get Contact Methods ===", () -> {
                ContactMethodResponse methods = ExampleHelpers.execute(client, client.contactInfo().getContactMethods(customerId));
                ExampleHelpers.pretty("Contact Methods", methods);
                if (methods.getContactMethods() != null && !methods.getContactMethods().isEmpty()) {
                    Destination first = methods.getContactMethods().get(0);
                    contactMethodId[0] = first.getId();
                    ExampleConsole.println("\n  First contact method ID: " + contactMethodId[0]);
                }
            });

            ExampleHelpers.runExampleStep("\n=== Step 2: Update Contact Info ===", () -> {
                ContactInfo info = new ContactInfo();
                ContactInfoEmail email = new ContactInfoEmail();
                email.setEmailAddress("updated+" + customerId.substring(0, 8) + "@example.com");
                info.setEmail(email);
                ExampleHelpers.execute(client, client.contactInfo().updateContactInfo(customerId, info));
                ExampleConsole.println("  Contact info updated successfully (no response body expected)");
            });

            if (contactMethodId[0] != null) {
                ExampleHelpers.runExampleStep("\n=== Step 3: Reset Password ===", () -> {
                    ResetPasswordRequest req = new ResetPasswordRequest();
                    req.setContactMethodId(contactMethodId[0]);
                    req.setProtocol(ResetPasswordRequest.ProtocolEnum.SMS);
                    ExampleHelpers.execute(client, client.registrationAndAccess().resetPassword(customerId, req));
                    ExampleConsole.println("  Password reset initiated successfully (OTP sent via SMS)");
                });
            } else {
                ExampleConsole.println("\n=== Step 3: Reset Password ===");
                ExampleConsole.println("  Skipped — no contactMethodId available from step 1");
            }

            ExampleHelpers.runExampleStep("\n=== Step 4: Unlock User ===", () -> {
                try {
                    UserStatus1 status = ExampleHelpers.execute(client, client.profileAndStatus()
                            .getUserStatus(customerId, "CUSTOMER_ID"));
                    if (status.getLocked() == null || !status.getLocked()) {
                        ExampleConsole.println("  Skipped: user is not in locked status — unlock not needed");
                        return;
                    }
                    ExampleHelpers.execute(client, client.registrationAndAccess().unlockUser(customerId));
                    ExampleConsole.println("  User unlocked successfully (no response body expected)");
                } catch (NotFoundException e) {
                    if (String.valueOf(e).toLowerCase().contains("locked")) {
                        ExampleConsole.println("  Skipped: user is not in locked status — unlock not needed");
                    } else {
                        throw e;
                    }
                }
            });
        }
        ExampleConsole.done();
    }
}
