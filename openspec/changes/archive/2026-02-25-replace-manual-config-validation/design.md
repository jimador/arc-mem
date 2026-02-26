## Context

`AnchorConfiguration.validateConfiguration()` is a `@PostConstruct` method that manually validates ~25 constraints across `DiceAnchorsProperties` nested records. Spring Boot natively supports Jakarta Bean Validation on `@ConfigurationProperties` records via `@Validated` — this is the idiomatic approach. The manual method duplicates framework capability, requires two dedicated test classes, and produces inconsistent error messages.

`DiceAnchorsProperties` is a deeply nested record tree with `@ConfigurationProperties(prefix = "dice-anchors")` and `@NestedConfigurationProperty` annotations. All nested configs are records. Nullable sub-configs (`conflict`, `retrieval`, `invariants`, `chatSeed`) use `@Nullable` from jspecify.

```mermaid
graph TD
    A[application.yml] -->|bind| B[DiceAnchorsProperties]
    B -->|@Validated| C[Jakarta Bean Validation]
    C -->|fail| D[BindValidationException at startup]
    C -->|pass| E[Application starts]
```

Current flow:
```mermaid
graph TD
    A[application.yml] -->|bind| B[DiceAnchorsProperties]
    B -->|inject| C[AnchorConfiguration]
    C -->|@PostConstruct| D[validateConfiguration]
    D -->|fail| E[IllegalStateException]
    D -->|pass| F[Application starts]
```

## Goals / Non-Goals

**Goals:**
- Replace all manual `@PostConstruct` validation with Jakarta Bean Validation annotations
- Preserve every existing constraint (same ranges, same semantics)
- Delete `AnchorConfigurationValidationTest.java` and `RetrievalConfigValidationTest.java`
- Reduce test count by removing tests that solely exercise removed validation code

**Non-Goals:**
- Changing any constraint values or ranges
- Adding new constraints not currently enforced
- Modifying `AnchorRepository.provision()` (operational `@PostConstruct`, not validation)
- Refactoring `DiceAnchorsProperties` record structure

## Decisions

### D1: `@Validated` on the properties record

Add `@Validated` to `DiceAnchorsProperties`. Spring Boot automatically validates `@ConfigurationProperties` beans annotated with `@Validated` at bind time. This replaces the entire `@PostConstruct` lifecycle hook.

**Alternative**: Keep `@PostConstruct` and just simplify — rejected because the framework handles this natively with better error formatting.

### D2: Standard annotations for simple constraints

Most checks map directly to standard Jakarta annotations:

| Manual check | Jakarta annotation |
|---|---|
| `budget > 0` | `@Positive` |
| `value in [0.0, 1.0]` | `@DecimalMin("0.0") @DecimalMax("1.0")` |
| `value in (0.0, 1.0]` | `@DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")` |
| `value >= 0` | `@Min(0)` |
| `value in [100, 900]` | `@Min(100) @Max(900)` |
| `value > 0` | `@Positive` |
| `value in [-0.5, 0.5]` | `@DecimalMin("-0.5") @DecimalMax("0.5")` |

### D3: `@AssertTrue` methods for cross-field constraints

Three constraints are cross-field:
1. `minRank < maxRank` → `@AssertTrue` method on `AnchorConfig`
2. `replaceThreshold > demoteThreshold` → `@AssertTrue` method on `ConflictConfig`
3. `hotThreshold > warmThreshold` → `@AssertTrue` method on `TierConfig`
4. `scoring weights sum to 1.0` → `@AssertTrue` method on `ScoringConfig`
5. `initialRank in [minRank, maxRank]` → `@AssertTrue` method on `AnchorConfig`

**Alternative**: Custom `@Constraint` validator classes — rejected as over-engineered for five checks. `@AssertTrue` on a record method is idiomatic and zero-ceremony.

### D4: `@Valid` for cascading into nested records

Add `@Valid` to nested record components so validation cascades:
- `AnchorConfig anchor` → `@Valid`
- `AssemblyConfig assembly` → `@Valid`
- Nullable nested records (`ConflictConfig`, `RetrievalConfig`, etc.) → `@Valid` (Bean Validation skips null objects, matching current behavior where null sub-configs skip their checks)

### D5: Delete validation-only tests entirely

The `AnchorConfigurationValidationTest` (6 tests) and `RetrievalConfigValidationTest` (5 tests) only test the `@PostConstruct` validation. These are deleted entirely. We do NOT add replacement integration tests for Bean Validation — Spring Boot's own validation integration is well-tested; we trust the framework.

## Risks / Trade-offs

**[Error message format changes]** → Acceptable. `BindValidationException` produces structured constraint violation messages instead of hand-written strings. No code depends on the specific error text.

**[Startup failure type changes]** → `IllegalStateException` becomes `BindValidationException`. Any code catching `IllegalStateException` on startup would break — but no such code exists (these are fail-fast checks).

**[Nullable sub-config cascading]** → Bean Validation skips `@Valid` on null objects by default. This matches the current behavior where null sub-configs skip their validation blocks. No risk.
