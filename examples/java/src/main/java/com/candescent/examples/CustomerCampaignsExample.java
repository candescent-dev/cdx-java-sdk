package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.ClientConfig;
import com.candescent.di.Environment;
import com.candescent.di.generated.model.BasePageDTOListImportJobDTO;
import com.candescent.di.generated.model.FileMetadata;
import com.candescent.di.generated.model.GroupBaseResponseDTO;
import com.candescent.di.generated.model.GroupBaseRequestDTO;
import com.candescent.di.generated.model.Pageable;
import com.candescent.di.generated.model.UserList;
import com.candescent.di.generated.model.UserListsDTO;
import com.candescent.di.generated.model.UserListsWithFileMetadata;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Customer Campaigns: experience groups, jobs, audience, and promotions suite.
 *   1.  List Experience Groups
 *   2.  Create Experience Group  (unique name with timestamp)
 *   3.  Update Experience Group  (conditional on step 2)
 *   4.  Delete Experience Group  (conditional on step 2)
 *   5.  List Jobs
 *   6.  Get Job                  (conditional on step 5)
 *   7.  Get Job Errors           (conditional on step 5)
 *   8.  Create User List Metadata
 *   9.  Get User List Details
 *  10.  Get User List Status     (conditional on step 5 job ID)
 *  11.  Create User List (Promotions Suite)
 */
public class CustomerCampaignsExample {

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        // Audience metadata in stage is validated against FI id (apigee-audience-stg uses 05523).
        String fiId = ExampleHelpers.firstNonBlank(System.getenv("CANDESCENT_FI_ID"), "05523");
        boolean enableUserlistMetadata = "true".equalsIgnoreCase(
                System.getenv("CANDESCENT_ENABLE_USERLIST_METADATA"));
        boolean enablePromotionsSuite = "true".equalsIgnoreCase(
                System.getenv("CANDESCENT_ENABLE_PROMOTIONS_SUITE"));

        try (CandescentClient client = ExampleHelpers.clientFromEnv();
                CandescentClient campaignsClient = createCampaignsClient()) {
            ExampleConsole.println("  FI_ID: " + fiId);
            ExampleConsole.println("Client initialised\n");

            // ── 1. List Experience Groups ────────────────────────────────────
            ExampleHelpers.runExampleStep("=== 1. List Experience Groups ===", () -> {
                Pageable pageable = new Pageable();
                pageable.setPage(0);
                pageable.setSize(10);
                ExampleHelpers.pretty("Experience Groups",
                        ExampleHelpers.execute(client, client.experienceGroups().listExperienceGroups().pageable(pageable)));
            });

            // ── 2. Create Experience Group (unique name) ─────────────────────
            final String[] groupId = {null};
            // Use timestamp suffix to avoid "already exists" errors from prior runs
            String groupName = "SDK_TEST_GROUP_" + System.currentTimeMillis();
            ExampleHelpers.runExampleStep("\n=== 2. Create Experience Group ===", () -> {
                GroupBaseRequestDTO group = new GroupBaseRequestDTO();
                group.setGroupName(groupName);
                group.setGroupDescription("Created by Java SDK example");
                GroupBaseResponseDTO result = ExampleHelpers.execute(client, client.experienceGroups().createExperienceGroup(group));
                ExampleHelpers.pretty("Created Group", result);
                if (result != null) {
                    groupId[0] = result.getGroupId();
                    if (groupId[0] != null) ExampleConsole.println("\n  Created group ID: " + groupId[0]);
                }
            });

            // ── 3. Update Experience Group ───────────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    groupId[0] != null,
                    "\n  Skipping update (no group ID from create step)",
                    "\n=== 3. Update Experience Group ===",
                    () -> {
                        GroupBaseRequestDTO upd = new GroupBaseRequestDTO();
                        upd.setGroupName(groupName + "_UPDATED");
                        upd.setGroupDescription("Updated by Java SDK");
                        ExampleHelpers.pretty("Updated Group",
                                ExampleHelpers.execute(client, client.experienceGroups()
                                        .updateExperienceGroup(groupId[0], upd)));
                    });

            // ── 4. Delete Experience Group ───────────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    groupId[0] != null,
                    "\n  Skipping delete (no group ID from create step)",
                    "\n=== 4. Delete Experience Group ===",
                    () -> {
                        ExampleHelpers.execute(client, client.experienceGroups().deleteExperienceGroup(groupId[0]));
                        ExampleConsole.println("  Experience group deleted successfully");
                    });

            // ── 5. List Jobs ─────────────────────────────────────────────────
            final String[] firstJobId = {null};
            ExampleHelpers.runExampleStep("\n=== 5. List Jobs ===", () -> {
                BasePageDTOListImportJobDTO jobs = ExampleHelpers.execute(client, client.jobs().listJobs());
                ExampleHelpers.pretty("Jobs", jobs);
                if (jobs != null && jobs.getContent() != null && !jobs.getContent().isEmpty()) {
                    firstJobId[0] = jobs.getContent().get(0).getJobId();
                    if (firstJobId[0] != null)
                        ExampleConsole.println("\n  Auto-discovered job_id: " + firstJobId[0]);
                }
            });

            // ── 6. Get Job ───────────────────────────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    firstJobId[0] != null,
                    "\n  Skipping get job (no job_id available)",
                    "\n=== 6. Get Job ===",
                    () -> ExampleHelpers.pretty("Job (" + firstJobId[0] + ")",
                            ExampleHelpers.execute(client, client.jobs().getJob(firstJobId[0]))));

            // ── 7. Get Job Errors ────────────────────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    firstJobId[0] != null,
                    "\n  Skipping get job errors (no job_id available)",
                    "\n=== 7. Get Job Errors ===",
                    () -> ExampleHelpers.pretty("Job Errors (" + firstJobId[0] + ")",
                            ExampleHelpers.execute(client, client.jobs().getJobErrors(firstJobId[0]))));

            // ── 8. Create User List Metadata ─────────────────────────────────
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String userlistFileName = "FI" + fiId + "_filename_" + timestamp + ".csv";
            final boolean[] metadataCreated = {false};
            ExampleHelpers.runExampleStepWhen(
                    enableUserlistMetadata,
                    "\n  Skipping user list metadata (set CANDESCENT_ENABLE_USERLIST_METADATA=true to enable).",
                    "\n=== 8. Create User List Metadata ===",
                    () -> {
                        FileMetadata meta = new FileMetadata();
                        meta.setUserlistFileName(userlistFileName);
                        meta.setUserlistName("SDK_TEST_LIST");
                        meta.setUserlistOperation("CREATE");
                        meta.setUserlistDescription("Created by Java SDK example");
                        UserListsWithFileMetadata entry = new UserListsWithFileMetadata();
                        entry.setFileMetadata(meta);
                        UserListsDTO dto = new UserListsDTO();
                        dto.setUserLists(List.of(entry));
                        ExampleHelpers.execute(campaignsClient, campaignsClient.audience().createUserListMetadata().userListsDTO(dto));
                        ExampleConsole.println("  User list metadata created successfully");
                        metadataCreated[0] = true;
                    });

            // ── 9. Get User List Details ─────────────────────────────────────
            ExampleHelpers.runExampleStepWhen(
                    metadataCreated[0],
                    "\n  Skipping get user list details (metadata create step did not succeed)",
                    "\n=== 9. Get User List Details ===",
                    () -> ExampleHelpers.pretty("User List Details",
                            ExampleHelpers.execute(campaignsClient, campaignsClient.audience()
                                    .getUserListDetails(userlistFileName, "fileStatus"))));

            // ── 10. Get User List Status (Promotions Suite — OAuth V1 /pss paths) ─
            ExampleHelpers.runExampleStepWhen(
                    enablePromotionsSuite && firstJobId[0] != null,
                    !enablePromotionsSuite
                            ? "\n  Skipping get user list status — promotions suite requires OAuth V1 token "
                                    + "(set CANDESCENT_ENABLE_PROMOTIONS_SUITE=true to enable)."
                            : "\n  Skipping get user list status (no job_id available)",
                    "\n=== 10. Get User List Status ===",
                    () -> ExampleHelpers.pretty("User List Status",
                            ExampleHelpers.execute(campaignsClient,
                                    campaignsClient.promotionsSuite().getUserListStatus(fiId, firstJobId[0]))));

            // ── 11. Create User List (Promotions Suite — OAuth V1 /pss paths) ─
            ExampleHelpers.runExampleStepWhen(
                    enablePromotionsSuite,
                    "\n  Skipping — promotions suite requires OAuth V1 token "
                            + "(set CANDESCENT_ENABLE_PROMOTIONS_SUITE=true to enable).",
                    "\n=== 11. Create User List ===",
                    () -> {
                        UserList userList = new UserList();
                        userList.setName("sdk_java_userlist_" + System.currentTimeMillis());
                        userList.setUsers(List.of("member001", "member002"));
                        ExampleHelpers.pretty("Created User List",
                                ExampleHelpers.execute(campaignsClient,
                                        campaignsClient.promotionsSuite().createUserList(fiId, userList)));
                    });
        }
        ExampleConsole.done();
    }

    /** Audience metadata is validated against FI 05523 in stage (apigee-audience-stg). */
    private static CandescentClient createCampaignsClient() {
        String clientId = System.getenv("CANDESCENT_CLIENT_ID");
        String clientSecret = System.getenv("CANDESCENT_CLIENT_SECRET");
        String institutionId = ExampleHelpers.firstNonBlank(
                System.getenv("CANDESCENT_CAMPAIGNS_INSTITUTION_ID"),
                System.getenv("CANDESCENT_FI_ID"),
                "05523");

        ClientConfig config = new ClientConfig()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setInstitutionId(institutionId);
        String baseUrl = System.getenv("CANDESCENT_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            config.setBaseUrl(baseUrl);
        } else {
            config.setEnvironment(Environment.fromEnvName(System.getenv("CANDESCENT_ENVIRONMENT")));
        }
        CandescentClient client = new CandescentClient(config);
        ExampleHelpers.configureClientParsing(client);
        return client;
    }
}
