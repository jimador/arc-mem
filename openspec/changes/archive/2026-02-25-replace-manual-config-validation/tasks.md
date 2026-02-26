## 1. Add dependency

- [x] 1.1 Add `spring-boot-starter-validation` to `pom.xml`

## 2. Annotate DiceAnchorsProperties

- [x] 2.1 Add `@Validated` to `DiceAnchorsProperties` record
- [x] 2.2 Add `@Valid` to nested record components (`anchor`, `assembly`, `conflict`, `retrieval`, `tier` inside AnchorConfig, `tier` inside ConflictConfig, `scoring` inside RetrievalConfig)
- [x] 2.3 Add Jakarta annotations to `AnchorConfig` fields: `@Positive` on budget, `@DecimalMin`/`@DecimalMax` on thresholds, `@Min`/`@Max` on rank fields
- [x] 2.4 Add `@AssertTrue` methods to `AnchorConfig` for cross-field constraints: `minRank < maxRank`, `initialRank in [minRank, maxRank]`
- [x] 2.5 Add Jakarta annotations to `AssemblyConfig`: `@Min(0)` on promptTokenBudget
- [x] 2.6 Add Jakarta annotations to `TierConfig`: `@Min(100) @Max(900)` on thresholds, `@Positive` on decay multipliers, `@AssertTrue` for `hotThreshold > warmThreshold`
- [x] 2.7 Add Jakarta annotations to `ConflictConfig`: `@DecimalMin(value="0.0", inclusive=false) @DecimalMax("1.0")` on thresholds, `@AssertTrue` for `replaceThreshold > demoteThreshold`
- [x] 2.8 Add Jakarta annotations to `TierModifierConfig`: `@DecimalMin("-0.5") @DecimalMax("0.5")` on modifier fields
- [x] 2.9 Add Jakarta annotations to `RetrievalConfig`: `@DecimalMin("0.0") @DecimalMax("1.0")` on minRelevance, `@Positive` on topK fields
- [x] 2.10 Add Jakarta annotations to `ScoringConfig`: `@DecimalMin("0.0") @DecimalMax("1.0")` on weights, `@AssertTrue` for weight sum == 1.0

## 3. Remove manual validation

- [x] 3.1 Delete `validateConfiguration()` method and `@PostConstruct` import from `AnchorConfiguration.java`

## 4. Delete validation tests

- [x] 4.1 Delete `AnchorConfigurationValidationTest.java`
- [x] 4.2 Delete `RetrievalConfigValidationTest.java`

## 5. Verify

- [x] 5.1 `./mvnw clean compile -DskipTests` — compiles without errors
- [x] 5.2 `./mvnw test` — all remaining tests pass
