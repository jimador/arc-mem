## Why

`AnchorConfiguration.validateConfiguration()` is a 75-line `@PostConstruct` method that manually checks ~25 configuration invariants with hand-written `if`/`throw` logic. This is exactly what Jakarta Bean Validation exists for. The manual approach duplicates framework capability, requires dedicated test classes (`AnchorConfigurationValidationTest`, `RetrievalConfigValidationTest`) that inflate the test count, and produces inconsistent error messages. Spring Boot has first-class support for `@Validated` on `@ConfigurationProperties` records — we should use it.

## What Changes

- **BREAKING**: Remove `AnchorConfiguration.validateConfiguration()` `@PostConstruct` method entirely
- Add `spring-boot-starter-validation` dependency to `pom.xml`
- Add Jakarta Bean Validation annotations (`@Min`, `@Max`, `@Positive`, `@DecimalMin`, `@DecimalMax`) to `DiceAnchorsProperties` record fields
- Add `@Validated` to `DiceAnchorsProperties` so Spring triggers validation at bind time
- Add `@Valid` on nested record components to cascade validation
- Add custom `@AssertTrue` methods for cross-field constraints (replace > demote thresholds, hot > warm thresholds, scoring weight sum)
- Delete `AnchorConfigurationValidationTest.java` and `RetrievalConfigValidationTest.java`
- Delete any other tests that solely exercise the removed `@PostConstruct` validation

## Capabilities

### New Capabilities

_None — this is a refactoring of existing validation, not a new capability._

### Modified Capabilities

_None — no spec-level behavior changes. Configuration constraints remain identical; only the enforcement mechanism changes from manual `@PostConstruct` to Jakarta Bean Validation._

## Impact

- **`pom.xml`**: New `spring-boot-starter-validation` dependency
- **`DiceAnchorsProperties.java`**: Annotation additions on record components
- **`AnchorConfiguration.java`**: `validateConfiguration()` method removed, `@PostConstruct` import removed
- **Test files deleted**: `AnchorConfigurationValidationTest.java`, `RetrievalConfigValidationTest.java`
- **Runtime behavior**: Validation errors now surface at application startup as `BindValidationException` instead of `IllegalStateException`. Error messages change format but convey the same constraints.
