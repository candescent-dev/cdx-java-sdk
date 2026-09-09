# Candescent DI Java SDK Examples

Runnable examples for the [Candescent Digital Insight Java SDK](https://central.sonatype.com/artifact/com.candescent.forge/di-java-sdk) (`com.candescent.forge:di-java-sdk`).

This repository is self-contained: examples resolve the SDK from Maven. You do not need to clone or build the SDK source locally.

| Package | Version |
|---------|---------|
| Java SDK (`com.candescent.forge:di-java-sdk`) | **1.0.0** |
| Examples (`com.candescent.forge:di-java-examples`) | **1.0.0** |
| OpenAPI spec | **1.8.0** |

## Requirements

| Requirement | Details |
|-------------|---------|
| Java | **17+** |
| Maven | **3.8+** |

## SDK dependency

Examples are present in [`examples/java/`](examples/java/) and depend on the published SDK from Maven Central, configured in [`examples/java/pom.xml`](examples/java/pom.xml):

```xml
<properties>
  <di.sdk.version>1.0.0</di.sdk.version>
</properties>

<dependency>
  <groupId>com.candescent.forge</groupId>
  <artifactId>di-java-sdk</artifactId>
  <version>${di.sdk.version}</version>
</dependency>
```

Maven resolves **1.0.0** from Maven Central automatically when you run `mvn compile`. Override with `-Ddi.sdk.version=<version>` when testing a local SDK install.

## Configuration

Copy the credentials template and fill in your values:

```bash
cp .env.example .env
```

The batch runner loads repo-root `.env` automatically. Export variables manually if you prefer not to use `source .env`.

**Required for most examples:**

```bash
export CANDESCENT_CLIENT_ID="your-client-id"
export CANDESCENT_CLIENT_SECRET="your-client-secret"
export CANDESCENT_INSTITUTION_ID="your-institution-id"
export CANDESCENT_ENVIRONMENT="stage"   # sandbox | stage | production
```

**Common optional identifiers** (see [`.env.example`](.env.example) for the full list):

```bash
export CANDESCENT_HOST_USER_ID="..."
export CANDESCENT_LOGIN_ID="..."
export CANDESCENT_ACCOUNT_ID="..."
export CANDESCENT_CUSTOMER_ID="..."
```

**MX examples** use separate Apigee credentials:

```bash
export CANDESCENT_MX_CLIENT_ID="..."
export CANDESCENT_MX_CLIENT_SECRET="..."
export CANDESCENT_MX_INSTITUTION_ID="..."
```

**Business banking examples** use `CANDESCENT_BB_*` variables (also documented in `.env.example`).

## Compile

From the repo root:

```bash
source .env
cd examples/java
mvn compile
```

## Run a single example

```bash
cd examples/java
mvn exec:java -Dexec.mainClass="com.candescent.examples.AccountsExample"
```

Replace `AccountsExample` with any class from the [available examples](#available-examples) table below.

## Run all examples

From the repo root:

```bash
bash scripts/run-all-java-examples.sh
```

Output is written to [`logs/all-examples.log`](logs/all-examples.log). Use a custom log path:

```bash
bash scripts/run-all-java-examples.sh --log=/tmp/java-examples.log
```

## Run tests

Unit tests use MockWebServer and do not call live APIs:

```bash
cd examples/java
mvn test
```

## Repository layout

```
cdx-java-sdk/
├── examples/java/          # Maven project (21 runnable examples + tests)
├── scripts/
│   └── run-all-java-examples.sh
├── logs/                   # Batch run output (gitignored)
├── .env.example            # Credentials template
└── CHANGELOG.md
```

## Available examples

| Example | TypeScript equivalent | Description |
|---------|----------------------|-------------|
| `AccountsExample` | `accounts.ts` | List accounts, get by ID |
| `TransactionsExample` | `transactions.ts` | List account transactions |
| `AuthenticationExample` | `authentication.ts` | V1 token, auth-code, revoke |
| `AuthenticationLifecycleExample` | `authentication-lifecycle.ts` | Token lifecycle with close() |
| `PaginationExample` | `pagination.ts` | PageIterator patterns |
| `ErrorHandlingExample` | `error-handling.ts` | Typed exception handling |
| `MoneyMovementExample` | `money-movement.ts` | Recipients and transfers |
| `CustomerManagementExample` | `customer-management.ts` | Contact info, reset, unlock |
| `RegisterAndLookupExample` | `register-and-lookup.ts` | Register and lookup customer |
| `UserStatusExample` | `user-status.ts` | Query user status by ID type |
| `NotificationChannelsExample` | `notification-channels.ts` | Subscription CRUD |
| `DisclosuresExample` | `disclosures.ts` | Institution/user disclosures |
| `EstatementsExample` | `estatements.ts` | E-statement preferences |
| `CustomerCampaignsExample` | `customer-campaigns.ts` | Experience groups, jobs |
| `MxServiceExample` | `mx-service.ts` | MX platform, realtime, SSO, reporting |
| `AlertConfigurationExample` | `alert-configuration.ts` | Alert types and templates |
| `AlertDeliveryExample` | `alert-delivery.ts` | History and publish `Event1` |
| `AlertPreferencesExample` | `alert-preferences.ts` | User alert preferences |
| `BusinessEntitlementsExample` | `business-entitlements.ts` | BB entitlements |
| `BusinessPaymentsExample` | `business-payments.ts` | ACH and Wire payments |
| `BusinessRegistrationExample` | `business-registration.ts` | BB registration flow |

## License

See [LICENSE](LICENSE).
