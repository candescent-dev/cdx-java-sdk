# Changelog

All notable changes to the Candescent Digital Insight Java SDK examples repository
will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The Maven artifact (`com.candescent.forge:di-java-sdk`) version and the Candescent DI OpenAPI
specification version are tracked separately.

## [Unreleased]

### Changed
- Bump `di-java-sdk` **1.0.0** (`di.sdk.version` in `pom.xml`)
- Examples use `ExampleHelpers.execute()` to tolerate live API fields absent from OpenAPI-generated models (no SDK changes required)

## [1.0.0] - 2026-08-14

### Version alignment

| Package | Version |
|---------|---------|
| Java SDK (`com.candescent.forge:di-java-sdk`) | **1.0.0** |
| OpenAPI spec | **1.8.0** |

### Added
- Fully self-contained examples repository with local batch runner
- 21 runnable Java examples covering all API operations
- Single root [`pom.xml`](pom.xml) depending on published `com.candescent.forge:di-java-sdk`
- `scripts/run-all-java-examples.sh` batch runner with repo-root `.env` loading
- `.env.example` credentials template
- Consumption guide in `README.md`

### Changed
- Examples aligned with OpenAPI 1.8.0 / `di-java-sdk` 1.0.0 generated models
- Maven Central coordinates: `pkg:maven/com.candescent.forge/di-java-sdk@1.0.0`
