package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.model.CustomerInformation;
import com.candescent.di.generated.model.FICustomerRequest;
import com.candescent.di.generated.model.FICustomerRequestChannelInfos;
import com.candescent.di.generated.model.FICustomerRequestChannelInfosChannelInfoInner;
import com.candescent.di.generated.model.FICustomerRequestChannelInfosChannelInfoInnerCredential;
import com.candescent.di.generated.model.FICustomerRequestFiId;
import com.candescent.di.generated.model.FICustomerRequestHostCredential;
import com.candescent.di.generated.model.FICustomerRequestId;
import com.candescent.di.generated.model.FICustomerRequestPerson;
import com.candescent.di.generated.model.FICustomerRequestPersonContactInfo;
import com.candescent.di.generated.model.FICustomerRequestPersonContactInfoPostalAddressInner;
import com.candescent.di.generated.model.FICustomerRequestPersonContactInfoPhoneNumberInner;
import com.candescent.di.generated.model.FICustomerRequestPersonPersonName;
import com.candescent.di.generated.model.RegisterCustomerRequest;
import com.candescent.di.generated.model.RegisterCustomerResponse;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Register a customer and look them up via multiple API endpoints
 * (mirrors examples/typescript/register-and-lookup.ts).
 */
public class RegisterAndLookupExample {
    private static final List<String> FIRST_NAMES = List.of(
            "James", "Emma", "Liam", "Olivia", "Noah", "Ava", "William", "Sophia");
    private static final Pattern FI_CUSTOMER_GUID_RE = Pattern.compile("^[0-9a-f]{32}$", Pattern.CASE_INSENSITIVE);

    private static final String USER_ID_TYPE_INSTITUTION_USER_ID = "INSTITUTION_USER_ID";
    private static final String USER_ID_TYPE_HOST_USER_ID = "HOST_USER_ID";
    private static final String USER_ID_TYPE_LOGIN_ID = "LOGIN_ID";

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();

        String fiId = ExampleHelpers.envOrDefault("CANDESCENT_INSTITUTION_ID", "05523");
        String memberNumber = String.valueOf(ExampleHelpers.secureRandomInt(100_000_000, 1_000_000_000));
        String loginId = randomLoginId();

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised");
            ExampleConsole.println("  FI_ID:          " + fiId);
            ExampleConsole.println("  memberNumber:   " + memberNumber);
            ExampleConsole.println("  loginId:        " + loginId);

            String registeredGuid = registerCustomer(client, fiId, memberNumber, loginId);
            String customerGuid = resolveCustomerGuidForLookups(registeredGuid);
            ExampleConsole.println("\n  Using Customer GUID: " + ExampleHelpers.maskSensitive(customerGuid));
            runCustomerLookups(client, fiId, customerGuid);
        }
        ExampleConsole.done();
    }

    private static String registerCustomer(
            CandescentClient client, String fiId, String memberNumber, String loginId) {
        final String[] customerGuid = {null};
        ExampleHelpers.runExampleStep("\n=== Step 1: Register Customer ===", () -> {
            RegisterCustomerRequest request = buildRegistration(fiId, memberNumber, loginId);
            RegisterCustomerResponse response =
                    ExampleHelpers.execute(client, client.registrationAndAccess().register(fiId, request));
            ExampleHelpers.pretty("Registration Response", response);
            customerGuid[0] = extractCustomerGuid(response);
            if (customerGuid[0] != null) {
                ExampleConsole.println("\n  -> Customer GUID: " + ExampleHelpers.maskSensitive(customerGuid[0]));
            }
        }, "Registration");

        if (customerGuid[0] != null) {
            return customerGuid[0];
        }

        ExampleConsole.println("\n  NOTE: Registration may require valid member data in the FI core.");
        String envCustomerId = System.getenv("CANDESCENT_CUSTOMER_ID");
        if (envCustomerId != null && FI_CUSTOMER_GUID_RE.matcher(envCustomerId).matches()) {
            ExampleConsole.println(
                    "  Using CANDESCENT_CUSTOMER_ID: " + ExampleHelpers.maskSensitive(envCustomerId));
            return envCustomerId;
        }
        String fallback = System.getenv("FALLBACK_CUSTOMER_ID");
        if (fallback != null && !fallback.isBlank()) {
            ExampleConsole.println("  Using FALLBACK_CUSTOMER_ID: " + ExampleHelpers.maskSensitive(fallback));
            return fallback;
        }
        ExampleConsole.println("  No customer GUID available — using demo customer ID for lookup examples.");
        ExampleConsole.println("  Set FALLBACK_CUSTOMER_ID to use a real known customer GUID.");
        return "00000000-0000-0000-0000-000000000000";
    }

    private static String resolveCustomerGuidForLookups(String registeredGuid) {
        if (registeredGuid != null && FI_CUSTOMER_GUID_RE.matcher(registeredGuid).matches()) {
            return registeredGuid;
        }
        String envCustomerId = System.getenv("CANDESCENT_CUSTOMER_ID");
        if (envCustomerId != null && FI_CUSTOMER_GUID_RE.matcher(envCustomerId).matches()) {
            return envCustomerId;
        }
        String fallback = System.getenv("FALLBACK_CUSTOMER_ID");
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "00000000-0000-0000-0000-000000000000";
    }

    private static void runCustomerLookups(CandescentClient client, String fiId, String customerGuid) {
        final CustomerInformation[] customerInfo = {null};

        ExampleHelpers.runExampleStep(
                "\nLookup #3 — Get Customer Information  [/ux-users/v1/...]",
                () -> {
                    customerInfo[0] =
                            ExampleHelpers.execute(client, client.profileAndStatus().getInformation(customerGuid));
                    ExampleHelpers.pretty("Response #3 — Get Customer Information", customerInfo[0]);
                },
                "Lookup #3");

        InstitutionUserLookup instUserLookup = resolveInstitutionUserLookup(customerInfo[0]);
        ExampleHelpers.runExampleStepWhen(
                instUserLookup != null,
                "\n  Skipping Lookup #2 — no institution user ID (set CANDESCENT_INSTITUTION_USER_ID"
                        + " or ensure Lookup #3 returns userId)",
                "\nLookup #2 — Get Institution User  [/db-users/v1/...]",
                () -> {
                    ExampleConsole.println(
                            "  Using ID: "
                                    + ExampleHelpers.maskSensitive(instUserLookup.id())
                                    + " (source: "
                                    + instUserLookup.source()
                                    + ", userIdType: "
                                    + instUserLookup.userIdType()
                                    + ")");
                    ExampleHelpers.pretty(
                            "Response #2 — Get Institution User",
                            ExampleHelpers.execute(client, client.profileAndStatus()
                                    .getInstitutionUser(instUserLookup.id())
                                    .userIdType(instUserLookup.userIdType())));
                },
                "Lookup #2");

        ExampleHelpers.runExampleStep(
                "\nLookup #1 — Get FI Customer  [/bankingservices/v2/...]",
                () -> ExampleHelpers.pretty(
                        "Response #1 — Get FI Customer",
                        ExampleHelpers.execute(client, client.profileAndStatus().getCustomer(fiId, customerGuid))),
                "Lookup #1");
    }

    private static InstitutionUserLookup resolveInstitutionUserLookup(CustomerInformation customerInfo) {
        String institutionUserId = System.getenv("CANDESCENT_INSTITUTION_USER_ID");
        if (institutionUserId != null && !institutionUserId.isBlank()) {
            return new InstitutionUserLookup(
                    institutionUserId, USER_ID_TYPE_INSTITUTION_USER_ID, "CANDESCENT_INSTITUTION_USER_ID");
        }
        if (customerInfo != null && customerInfo.getUserId() != null && !customerInfo.getUserId().isBlank()) {
            return new InstitutionUserLookup(
                    customerInfo.getUserId(), USER_ID_TYPE_INSTITUTION_USER_ID, "userId from Lookup #3");
        }
        String hostUserId = System.getenv("CANDESCENT_HOST_USER_ID");
        if (hostUserId != null && !hostUserId.isBlank()) {
            return new InstitutionUserLookup(hostUserId, USER_ID_TYPE_HOST_USER_ID, "CANDESCENT_HOST_USER_ID");
        }
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");
        if (loginId != null && !loginId.isBlank()) {
            return new InstitutionUserLookup(loginId, USER_ID_TYPE_LOGIN_ID, "CANDESCENT_LOGIN_ID");
        }
        return null;
    }

    private record InstitutionUserLookup(String id, String userIdType, String source) {}

    private static RegisterCustomerRequest buildRegistration(String fiId, String memberNumber, String loginId) {
        RegisterCustomerRequest request = new RegisterCustomerRequest();

        FICustomerRequest fiCustomer = new FICustomerRequest();

        FICustomerRequestId id = new FICustomerRequestId();
        id.setValue("0");
        id.setType("GUID");
        fiCustomer.setId(id);

        FICustomerRequestFiId fiIdObj = new FICustomerRequestFiId();
        fiIdObj.setValue(fiId);
        fiCustomer.setFiId(fiIdObj);
        fiCustomer.setMemberNumber(memberNumber);

        FICustomerRequestPersonPersonName personName = new FICustomerRequestPersonPersonName();
        personName.setFirstName(ExampleHelpers.secureRandomChoice(FIRST_NAMES));
        personName.setLastName("ZionTest");

        FICustomerRequestPersonContactInfoPostalAddressInner address =
                new FICustomerRequestPersonContactInfoPostalAddressInner();
        address.setAddress1("123 Main Street");
        address.setCity("City Name");
        address.setState("NJ");
        address.setPostalCode("07047");
        address.setCountry("USA");

        FICustomerRequestPersonContactInfoPhoneNumberInner phone =
                new FICustomerRequestPersonContactInfoPhoneNumberInner();
        phone.setNumber("1231231231");
        phone.setCountryCode("0");

        FICustomerRequestPersonContactInfo contactInfo = new FICustomerRequestPersonContactInfo();
        contactInfo.setEmailAddress("ziontest+" + memberNumber + "@example.com");
        contactInfo.setPostalAddress(List.of(address));
        contactInfo.setPhoneNumber(List.of(phone));

        FICustomerRequestPerson person = new FICustomerRequestPerson();
        person.setPersonName(personName);
        person.setContactInfo(contactInfo);
        person.setBirthDate("1989-10-09");
        fiCustomer.setPerson(person);

        FICustomerRequestChannelInfosChannelInfoInnerCredential credential =
                new FICustomerRequestChannelInfosChannelInfoInnerCredential();
        credential.setLoginId(loginId);
        credential.setPassword(ExampleHelpers.registrationChannelPassword());

        FICustomerRequestChannelInfosChannelInfoInner channelInfo =
                new FICustomerRequestChannelInfosChannelInfoInner();
        channelInfo.setChannelType("TPV_API");
        channelInfo.setCredential(credential);

        FICustomerRequestChannelInfos channelInfos = new FICustomerRequestChannelInfos();
        channelInfos.setChannelInfo(List.of(channelInfo));
        fiCustomer.setChannelInfos(channelInfos);

        fiCustomer.setAcceptedDisclosure("false");
        fiCustomer.setUserType("PRIMARY");
        fiCustomer.setSsn("123456789");
        fiCustomer.setMotherMaidenName("MaidenName");

        FICustomerRequestHostCredential hostCredential = new FICustomerRequestHostCredential();
        hostCredential.setPassword(ExampleHelpers.registrationHostPassword());
        fiCustomer.setHostCredential(hostCredential);

        request.setFiCustomer(fiCustomer);
        return request;
    }

    private static String extractCustomerGuid(RegisterCustomerResponse response) {
        if (response.getFiCustomer() != null && response.getFiCustomer().getId() != null) {
            return response.getFiCustomer().getId().getValue();
        }
        return null;
    }

    private static String randomLoginId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        int len = ExampleHelpers.secureRandomInt(6, 13);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(ExampleHelpers.secureRandomInt(0, chars.length())));
        }
        return sb.toString();
    }
}
