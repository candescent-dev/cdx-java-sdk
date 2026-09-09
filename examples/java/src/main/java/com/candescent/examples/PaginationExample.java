package com.candescent.examples;

import com.candescent.di.CandescentClient;
import com.candescent.di.generated.ApiException;
import com.candescent.di.generated.model.AccountsResponse;
import com.candescent.di.generated.model.AlertPreferenceResources;
import com.candescent.di.generated.model.BaseGroupsWithParticipantsCountDTO;
import com.candescent.di.generated.model.Pageable;
import com.candescent.di.generated.model.SearchResponse;
import com.google.gson.JsonObject;
import com.candescent.di.generated.model.TransactionsResponse;
import com.candescent.di.pagination.Page;
import com.candescent.di.pagination.PageFetcher;
import com.candescent.di.pagination.PageIterator;
import com.candescent.di.pagination.PageRequest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pagination patterns: offset, HATEOAS, and cursor-based iteration with PageIterator.
 */
public class PaginationExample {
    private static final String PAGE_LOG_PREFIX = "  Page ";
    /** Keep banking-activities demos short — stage can return thousands of pages in 7 days. */
    private static final int BANKING_ACTIVITIES_PAGE_SIZE = 25;
    private static final int BANKING_ACTIVITIES_DEMO_PAGES = 3;

    public static void main(String[] args) throws Exception {
        ExampleHelpers.applyDemoFallbacksIfNeeded();
        String fiId = ExampleHelpers.envOrDefault("CANDESCENT_FI_ID",
                ExampleHelpers.envOrDefault("CANDESCENT_INSTITUTION_ID", "05523"));
        String hostUserId = ExampleHelpers.envOrDefault("CANDESCENT_HOST_USER_ID", "demo-host-user");
        String loginId = System.getenv("CANDESCENT_LOGIN_ID");

        ExampleConsole.println("  FI_ID:        " + fiId);
        ExampleConsole.println("  HOST_USER_ID: " + ExampleHelpers.envSetStatus(hostUserId));
        ExampleConsole.println("  LOGIN_ID:     " + ExampleHelpers.envSetStatus(loginId));
        ExampleConsole.println();

        try (CandescentClient client = ExampleHelpers.clientFromEnv()) {
            ExampleConsole.println("Client initialised\n");

            String accountId = resolveAccountId(client, hostUserId, loginId);
            if (accountId != null) {
                ExampleConsole.println("  ACCOUNT_ID:   " + accountId);
            }

            PageFetcher<Object> offsetFetcher = createOffsetFetcher(client, accountId, hostUserId, loginId);
            runOffsetPattern(offsetFetcher);

            runHateoasPattern(client);
            runCursorPattern(client);
        }

        printSummary();
        ExampleConsole.done("Done.");
    }

    private static void runOffsetPattern(PageFetcher<Object> fetcher) {
        printPatternHeader("PATTERN 1: pageNo / pageSize or $skip / $top", false);
        ExampleHelpers.runExampleStep("\n--- 1a. Manual pagination loop ---", () -> runManualOffsetLoop(fetcher));
        ExampleHelpers.runExampleStep("\n--- 1b. Using PageIterator ---", () -> runOffsetPageIteratorDemo(fetcher));
    }

    private static void runManualOffsetLoop(PageFetcher<Object> fetcher) throws Exception {
        int pageNo = 0;
        int pageSize = 10;
        List<Object> all = new ArrayList<>();
        boolean continuing = true;
        while (continuing) {
            continuing = appendOffsetPage(fetcher, pageNo, pageSize, all);
            if (continuing) {
                pageNo++;
            }
        }
        ExampleConsole.println("  Total items: " + all.size());
    }

    private static boolean appendOffsetPage(
            PageFetcher<Object> fetcher, int pageNo, int pageSize, List<Object> all) throws Exception {
        Page<Object> page = fetcher.fetch(new PageRequest(pageNo, pageSize, null));
        List<Object> batch = page.getItems() != null ? page.getItems() : List.of();
        if (batch.isEmpty()) {
            return false;
        }
        ExampleConsole.println(PAGE_LOG_PREFIX + pageNo + ": " + batch.size() + " item(s)");
        all.addAll(batch);
        return page.isHasMore();
    }

    private static void runOffsetPageIteratorDemo(PageFetcher<Object> fetcher) throws Exception {
        PageIterator<Object> iterator = new PageIterator<>(fetcher, new PageRequest(0, 10, null));
        int count = 0;
        for (Object ignored : iterator) {
            count++;
        }
        ExampleConsole.println("  Iterated " + count + " item(s)");
        ExampleConsole.println("  Total via toList(): " + new PageIterator<>(fetcher).toList().size());
    }

    private static void runHateoasPattern(CandescentClient client) {
        printPatternHeader("PATTERN 2: Spring HATEOAS (Experience Groups)", true);
        ExampleHelpers.runExampleStep("\n--- 2a. Manual pagination loop ---", () -> runManualExperienceGroupsLoop(client));
        ExampleHelpers.runExampleStep("\n--- 2b. Using PageIterator ---", () -> runExperienceGroupsPageIteratorDemo(client));
    }

    private static void runManualExperienceGroupsLoop(CandescentClient client) throws Exception {
        int pageNum = 0;
        int pageSize = 10;
        List<Object> allGroups = new ArrayList<>();
        boolean continuing = true;
        while (continuing) {
            continuing = appendExperienceGroupsPage(client, pageNum, pageSize, allGroups);
            if (continuing) {
                pageNum++;
            }
        }
        ExampleConsole.println("  Total experience groups: " + allGroups.size());
    }

    private static boolean appendExperienceGroupsPage(
            CandescentClient client, int pageNum, int pageSize, List<Object> allGroups) throws Exception {
        Pageable pageable = new Pageable();
        pageable.setPage(pageNum);
        pageable.setSize(pageSize);
        BaseGroupsWithParticipantsCountDTO response = ExampleHelpers.execute(client, client.experienceGroups()
                .listExperienceGroups()
                .pageable(pageable));
        var content = response.getContent() != null ? response.getContent() : List.of();
        var pageMeta = response.getPage();
        if (isEmptyExperienceGroupsPage(content, pageMeta)) {
            return false;
        }
        ExampleConsole.println(PAGE_LOG_PREFIX + pageNum + ": " + content.size() + " groups");
        allGroups.addAll(content);
        return shouldContinueExperienceGroups(content, pageMeta, pageNum, pageSize);
    }

    private static void runExperienceGroupsPageIteratorDemo(CandescentClient client) throws Exception {
        PageFetcher<Object> fetchGroups = createExperienceGroupsFetcher(client);
        PageIterator<Object> iterator = new PageIterator<>(fetchGroups, new PageRequest(0, 10, null));
        int printed = 0;
        for (Object group : iterator) {
            if (printed < 5) {
                ExampleConsole.println("  Group: " + group.getClass().getSimpleName());
                printed++;
            }
        }
        ExampleConsole.println("  Iteration complete");
    }

    private static void runCursorPattern(CandescentClient client) {
        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusDays(7);
        printPatternHeader("PATTERN 3: Cursor (nextPageToken) — Banking Activities", true);
        ExampleHelpers.runExampleStep("\n--- 3a. Manual cursor loop ---",
                () -> runBankingActivitiesStep(client, c -> runManualBankingActivitiesLoop(c, start, end)));
        ExampleHelpers.runExampleStep("\n--- 3b. Using PageIterator ---",
                () -> runBankingActivitiesStep(client, c -> runBankingActivitiesPageIteratorDemo(c, start, end)));
    }

    @FunctionalInterface
    @SuppressWarnings("java:S112")
    private interface BankingActivitiesStep {
        void run(CandescentClient client) throws Exception;
    }

    private static void runBankingActivitiesStep(CandescentClient client, BankingActivitiesStep step) throws Exception {
        try {
            step.run(client);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                ExampleConsole.println("  Skipping — banking activities search returned 404 for this institution.");
                return;
            }
            throw e;
        }
    }

    private static void runManualBankingActivitiesLoop(
            CandescentClient client, OffsetDateTime start, OffsetDateTime end) throws Exception {
        String nextToken = null;
        int pageIndex = 0;
        int total = 0;
        boolean continuing = true;
        while (continuing && pageIndex < BANKING_ACTIVITIES_DEMO_PAGES) {
            BankingActivitiesPage page = fetchBankingActivitiesPage(client, start, end, nextToken);
            if (page.noRecords()) {
                ExampleConsole.println(PAGE_LOG_PREFIX + pageIndex + ": no records");
                continuing = false;
            } else {
                ExampleConsole.println(PAGE_LOG_PREFIX + pageIndex + ": " + page.batchSize() + " activities");
                total += page.batchSize();
                nextToken = page.nextToken();
                continuing = page.hasMore();
                if (continuing) {
                    pageIndex++;
                }
            }
        }
        ExampleConsole.println("  Total banking activities (demo, first " + BANKING_ACTIVITIES_DEMO_PAGES
                + " pages): " + total);
    }

    private record BankingActivitiesPage(int batchSize, String nextToken, boolean noRecords) {
        boolean hasMore() {
            return nextToken != null && !nextToken.isBlank() && batchSize > 0;
        }
    }

    private static BankingActivitiesPage fetchBankingActivitiesPage(
            CandescentClient client, OffsetDateTime start, OffsetDateTime end, String nextToken)
            throws Exception {
        SearchResponse response = searchBankingActivities(client, start, end, BANKING_ACTIVITIES_PAGE_SIZE, nextToken);
        if (response == null) {
            return new BankingActivitiesPage(0, null, true);
        }
        var batch = response.getBankingActivities() != null ? response.getBankingActivities() : List.of();
        return new BankingActivitiesPage(batch.size(), response.getNextPageToken(), false);
    }

    private static void runBankingActivitiesPageIteratorDemo(
            CandescentClient client, OffsetDateTime start, OffsetDateTime end) throws Exception {
        PageFetcher<Object> fetchActivities = createBankingActivitiesFetcher(client, start, end);
        int takeCount = BANKING_ACTIVITIES_PAGE_SIZE * BANKING_ACTIVITIES_DEMO_PAGES;
        List<Object> sample = new PageIterator<>(fetchActivities,
                new PageRequest(0, BANKING_ACTIVITIES_PAGE_SIZE, null)).take(takeCount);
        ExampleConsole.println("  PageIterator.take(" + takeCount + "): " + sample.size() + " activities");
    }

    private static void printPatternHeader(String title, boolean leadingNewline) {
        ExampleConsole.println((leadingNewline ? "\n" : "") + "=".repeat(72));
        ExampleConsole.println("  " + title);
        ExampleConsole.println("=".repeat(72));
    }

    private static boolean isEmptyExperienceGroupsPage(
            List<?> content, com.candescent.di.generated.model.Page pageMeta) {
        return content.isEmpty()
                && (pageMeta == null
                        || pageMeta.getTotalElements() == null
                        || pageMeta.getTotalElements() == 0);
    }

    private static PageFetcher<Object> createOffsetFetcher(
            CandescentClient client, String accountId, String hostUserId, String loginId) {
        return req -> {
            try {
                return fetchPreferencesPage(client, hostUserId, loginId, req);
            } catch (ApiException e) {
                if (accountId == null) {
                    return new Page<>(List.of(), false, null, null);
                }
                return fetchTransactionsFallbackPage(client, accountId, hostUserId, loginId, req);
            }
        };
    }

    private static Page<Object> fetchPreferencesPage(
            CandescentClient client, String hostUserId, String loginId, PageRequest req) throws ApiException {
        var builder = client.userPreferences().callList().pageNo(req.getPage()).pageSize(req.getSize());
        if (hostUserId != null) {
            builder.hostUserId(hostUserId);
        } else if (loginId != null) {
            builder.loginId(loginId);
        }
        AlertPreferenceResources response = ExampleHelpers.execute(client, builder);
        var prefs = response.getAlertPreferences() != null ? response.getAlertPreferences() : List.of();
        return new Page<>(
                new ArrayList<>(prefs),
                prefs.size() >= (req.getSize() != null ? req.getSize() : 10),
                null,
                null);
    }

    private static Page<Object> fetchTransactionsFallbackPage(
            CandescentClient client,
            String accountId,
            String hostUserId,
            String loginId,
            PageRequest req) throws ApiException {
        int pageSize = req.getSize() != null ? req.getSize() : 10;
        int page = req.getPage() != null ? req.getPage() : 0;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        var txBuilder = client.transactions().listAccountTransactions(accountId)
                .startDate(start).endDate(end)
                .$skip(page * pageSize).$top(pageSize);
        if (hostUserId != null) {
            txBuilder.hostUserId(hostUserId);
        } else if (loginId != null) {
            txBuilder.loginId(loginId);
        }
        TransactionsResponse txResponse = ExampleHelpers.execute(client, txBuilder);
        List<?> txns = txResponse != null ? txResponse.getTransactions() : null;
        List<Object> items = txns != null
                ? new ArrayList<>(txns.stream().map(t -> (Object) t).toList())
                : new ArrayList<>();
        return new Page<>(items, items.size() >= pageSize, null, null);
    }

    private static boolean shouldContinueExperienceGroups(
            List<?> content,
            com.candescent.di.generated.model.Page pageMeta,
            int pageNum,
            int pageSize) {
        if (pageMeta != null && pageMeta.getTotalPages() != null && pageNum + 1 >= pageMeta.getTotalPages()) {
            return false;
        }
        return content.size() >= pageSize;
    }

    private static PageFetcher<Object> createExperienceGroupsFetcher(CandescentClient client) {
        return req -> {
            Pageable pageable = new Pageable();
            pageable.setPage(req.getPage() != null ? req.getPage() : 0);
            pageable.setSize(req.getSize() != null ? req.getSize() : 10);
            BaseGroupsWithParticipantsCountDTO response = ExampleHelpers.execute(client, client.experienceGroups()
                    .listExperienceGroups()
                    .pageable(pageable));
            var content = response.getContent() != null
                    ? new ArrayList<Object>(response.getContent())
                    : new ArrayList<Object>();
            var pageMeta = response.getPage();
            int currentPage = req.getPage() != null ? req.getPage() : 0;
            int totalPages = (pageMeta != null && pageMeta.getTotalPages() != null)
                    ? pageMeta.getTotalPages().intValue() : 0;
            return new Page<>(content, currentPage + 1 < totalPages,
                    null, pageMeta != null ? pageMeta.getTotalElements() : null);
        };
    }

    private static SearchResponse searchBankingActivities(
            CandescentClient client,
            OffsetDateTime start,
            OffsetDateTime end,
            long pageSize,
            String nextPageToken)
            throws ApiException {
        JsonObject body = bankingActivitiesRequestBody(start, end, pageSize, nextPageToken);
        return ExampleHelpers.postJson(client, "/v1/banking-activities", body, SearchResponse.class);
    }

    /** API rejects generated SearchCriteria bodies (empty arrays, malformed UTC "ZZ" dates). */
    private static JsonObject bankingActivitiesRequestBody(
            OffsetDateTime start, OffsetDateTime end, long pageSize, String nextPageToken) {
        JsonObject body = new JsonObject();
        body.addProperty("startTime", toApiInstant(start));
        body.addProperty("endTime", toApiInstant(end));
        body.addProperty("pageSize", pageSize);
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            body.addProperty("nextPageToken", nextPageToken);
        }
        return body;
    }

    private static String toApiInstant(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS).toInstant().toString();
    }

    private static PageFetcher<Object> createBankingActivitiesFetcher(
            CandescentClient client, OffsetDateTime start, OffsetDateTime end) {
        return req -> {
            long pageSize = req.getSize() != null ? req.getSize() : BANKING_ACTIVITIES_PAGE_SIZE;
            SearchResponse response = searchBankingActivities(client, start, end, pageSize, req.getPageToken());
            if (response == null) {
                return new Page<>(List.of(), false, null, null);
            }
            var batch = response.getBankingActivities() != null
                    ? new ArrayList<Object>(response.getBankingActivities())
                    : new ArrayList<Object>();
            String nextTok = response.getNextPageToken();
            boolean hasMore = nextTok != null && !nextTok.isBlank() && !batch.isEmpty();
            return new Page<>(batch, hasMore, nextTok, null);
        };
    }

    private static String resolveAccountId(CandescentClient client, String hostUserId, String loginId) {
        String fromEnv = System.getenv("CANDESCENT_ACCOUNT_ID");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        try {
            var builder = client.accounts().callList();
            if (hostUserId != null) {
                builder.hostUserId(hostUserId);
            } else if (loginId != null) {
                builder.loginId(loginId);
            }
            AccountsResponse accountsResponse = ExampleHelpers.execute(client, builder);
            List<com.candescent.di.generated.model.Account> accounts = accountsResponse != null
                    ? accountsResponse.getAccounts()
                    : null;
            if (accounts != null && !accounts.isEmpty()) {
                return accounts.get(0).getId();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static void printSummary() {
        ExampleConsole.println("""
            
            ========================================================================
              PAGINATION PATTERNS SUMMARY
            ========================================================================
              pageNo/pageSize     → User Preferences (alert prefs)
              Spring HATEOAS      → Experience Groups
              nextPageToken       → Banking Activities
              PageIterator        → Iterable + toList() + take(n)
            """);
    }
}
