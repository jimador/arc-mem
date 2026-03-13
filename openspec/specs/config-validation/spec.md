## ADDED Requirements

### Requirement: Jakarta Bean Validation on configuration properties

`ArcMemProperties` SHALL use Jakarta Bean Validation annotations to enforce all configuration constraints. The `@Validated` annotation MUST be present on the properties record. Nested record components that contain constrained fields MUST be annotated with `@Valid` to cascade validation.

Cross-field constraints (rank range ordering, threshold ordering, scoring weight sum) SHALL use `@AssertTrue` methods on the containing record.

#### Scenario: Invalid configuration rejected at startup
- **WHEN** `application.yml` contains a configuration value that violates a constraint (e.g., `budget: 0`, `auto-activate-threshold: 1.5`)
- **THEN** the application SHALL fail to start with a `BindValidationException`

#### Scenario: Nullable sub-configs skip validation when absent
- **WHEN** a nullable nested config (e.g., `conflict`, `retrieval`) is not present in `application.yml`
- **THEN** validation SHALL pass without error (Bean Validation skips `@Valid` on null objects)

## REMOVED Requirements

### Requirement: Manual @PostConstruct configuration validation
**Reason**: Replaced by Jakarta Bean Validation annotations on `ArcMemProperties`. All constraints are preserved; only the enforcement mechanism changes.
**Migration**: Remove `ArcMemConfiguration.validateConfiguration()` method. Constraint violations now surface as `BindValidationException` instead of `IllegalStateException`.
