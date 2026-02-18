## ADDED Requirements

### Requirement: SimulationAssertion SPI

A `SimulationAssertion` interface SHALL define the assertion SPI with a single method: `evaluate(SimulationResult result) -> AssertionResult`. `AssertionResult` SHALL be a record containing: `name` (String, assertion identifier), `passed` (boolean), and `details` (String, human-readable explanation of pass/fail reason). All assertion implementations SHALL implement this interface.

#### Scenario: Assertion returns pass
- **WHEN** an assertion evaluates a simulation result that meets its criteria
- **THEN** the returned AssertionResult has passed=true with a success message

#### Scenario: Assertion returns fail with details
- **WHEN** an assertion evaluates a simulation result that violates its criteria
- **THEN** the returned AssertionResult has passed=false with a detail message explaining the violation

### Requirement: Nine concrete assertion implementations

Nine concrete assertions SHALL be implemented:

1. `AnchorCountAssertion` — verifies the final active anchor count is within a specified [min, max] range.
2. `RankDistributionAssertion` — verifies anchor ranks meet a distribution constraint (e.g., at least N anchors above rank R).
3. `TrustScoreRangeAssertion` — verifies all anchor trust scores fall within [min, max].
4. `PromotionZoneAssertion` — verifies expected promotion zone distribution (e.g., at least N in AUTO_PROMOTE).
5. `AuthorityAtMostAssertion` — verifies no anchor exceeds a specified authority level.
6. `KgContextContainsAssertion` — verifies the final anchor context contains text matching specified patterns.
7. `KgContextEmptyAssertion` — verifies the anchor context is empty (for testing injection-disabled runs).
8. `NoCanonAutoAssignedAssertion` — verifies CANON authority was never assigned automatically during the run.
9. `CompactionIntegrityAssertion` — verifies compaction did not lose critical ground truth facts.

#### Scenario: AnchorCountAssertion passes within range
- **WHEN** the assertion specifies min=5, max=15 and the final anchor count is 10
- **THEN** the assertion passes

#### Scenario: AnchorCountAssertion fails below minimum
- **WHEN** the assertion specifies min=5, max=15 and the final anchor count is 3
- **THEN** the assertion fails with details "Expected anchor count in [5, 15], got 3"

#### Scenario: RankDistributionAssertion checks threshold
- **WHEN** the assertion specifies "at least 3 anchors above rank 600"
- **AND** 4 anchors have rank > 600
- **THEN** the assertion passes

#### Scenario: NoCanonAutoAssignedAssertion checks all turns
- **WHEN** the assertion evaluates a run where CANON was never auto-assigned
- **THEN** the assertion passes

### Requirement: AssertionConfig YAML record

An `AssertionConfig` record SHALL be YAML-deserializable with fields: `type` (String, maps to assertion class name or short alias like "anchor-count"), and `params` (Map<String, Object> containing assertion-specific parameters). The aliases SHALL map to concrete implementations: "anchor-count" -> `AnchorCountAssertion`, "rank-distribution" -> `RankDistributionAssertion`, "trust-score-range" -> `TrustScoreRangeAssertion`, "promotion-zone" -> `PromotionZoneAssertion`, "authority-at-most" -> `AuthorityAtMostAssertion`, "kg-context-contains" -> `KgContextContainsAssertion`, "kg-context-empty" -> `KgContextEmptyAssertion`, "no-canon-auto-assigned" -> `NoCanonAutoAssignedAssertion`, "compaction-integrity" -> `CompactionIntegrityAssertion`.

#### Scenario: YAML deserialization
- **WHEN** a scenario YAML contains `assertions: [{ type: "anchor-count", params: { min: 5, max: 20 } }]`
- **THEN** the `AssertionConfig` deserializes with type="anchor-count" and params={min=5, max=20}

#### Scenario: Type alias resolution
- **WHEN** type="trust-score-range" is specified
- **THEN** the framework resolves it to `TrustScoreRangeAssertion`

### Requirement: Assertions field in SimulationScenario

The `SimulationScenario` record SHALL include an `assertions` field of type `List<AssertionConfig>`. The field SHALL default to an empty list when not specified in YAML (ensuring backward compatibility). The `SimulationService` SHALL evaluate all configured assertions after the simulation completes and include the results in the `SimulationRunRecord`.

#### Scenario: Scenario with assertions
- **WHEN** a scenario YAML includes 3 assertion configs
- **THEN** `SimulationScenario.assertions()` returns a list of 3 AssertionConfig entries

#### Scenario: Scenario without assertions
- **WHEN** a scenario YAML has no `assertions` field
- **THEN** `SimulationScenario.assertions()` returns an empty list

#### Scenario: Assertions evaluated on completion
- **WHEN** a simulation completes for a scenario with 2 configured assertions
- **THEN** both assertions are evaluated and their results are included in the SimulationRunRecord

### Requirement: Assertion results display in UI

Assertion results SHALL be displayed in the `DriftSummaryPanel` as a section below the 6-stat grid. Each assertion SHALL show its name, a pass/fail badge (green for pass, magenta for fail), and the details string. Assertion results SHALL also be displayed in the `RunInspectorView` in a dedicated "Assertions" section. Failed assertions SHALL be visually prominent (e.g., highlighted border or background).

#### Scenario: Passed assertion in DriftSummaryPanel
- **WHEN** the simulation completes with an assertion that passed
- **THEN** the DriftSummaryPanel shows the assertion name with a green "PASS" badge

#### Scenario: Failed assertion in DriftSummaryPanel
- **WHEN** the simulation completes with an assertion that failed
- **THEN** the DriftSummaryPanel shows the assertion name with a magenta "FAIL" badge and the failure details

#### Scenario: Assertions in RunInspectorView
- **WHEN** the user opens the RunInspectorView for a run with assertion results
- **THEN** the Assertions section lists all assertion results with pass/fail status and details
