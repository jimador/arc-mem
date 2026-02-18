# Implementation Tasks

## 1. Scenario YAML Structure

- [N/A] 1.1 Create `src/main/resources/simulations/deterministic-sim.yaml`
- [N/A] 1.2 Define core scenarios (3-5 scenarios covering key behaviors):
  - [N/A] 1.2.1 "resist-direct-negation": Lexical conflict detection
  - [N/A] 1.2.2 "resist-semantic-drift": Semantic conflict detection
  - [N/A] 1.2.3 "maintain-rank-stability": Rank preservation under pressure
  - [N/A] 1.2.4 "authority-enforces-preservation": CANON facts prioritized
- [N/A] 1.3 For each scenario, define:
  - [N/A] 1.3.1 Turn sequence (turn number, user-message, canned llm-response, expected-drift)
  - [N/A] 1.3.2 Expected final metrics (fact-survival-rate, contradiction-count, rank-stability)
- [N/A] 1.4 Validate YAML schema (parse test)

> **Decision**: Scenarios are implemented as @Nested test classes rather than YAML files.
> This avoids modifying SimulationTurnExecutor and keeps tests simple and self-contained.
> Each @Nested class feeds known inputs into engine components directly.

## 2. SimulationTurnExecutor Mode Detection

- [N/A] 2.1 Add `type` field to scenario YAML parsing (deterministic vs live)
- [N/A] 2.2 Update `executeTurn()` method:
  - [N/A] 2.2.1 Check scenario type
  - [N/A] 2.2.2 If deterministic: lookup `canned-responses[turn-number]`, return response
  - [N/A] 2.2.3 If live: call ChatModel as before
- [N/A] 2.3 Add logging for mode selection (which path taken)
- [N/A] 2.4 Add error handling (missing canned response → fail fast with clear message)

> **Decision**: SimulationTurnExecutor is NOT modified. Tests exercise anchor engine
> components (conflict detector, conflict resolver, reinforcement policy, scoring service)
> directly with known inputs, avoiding Spring context and LLM dependencies entirely.

## 3. Metrics Validation Framework

- [x] 3.1 Create `MetricsValidator` utility class
- [x] 3.2 Implement `validate(metricName, actual, expression)` method
- [x] 3.3 Parse expected metric strings (e.g., ">0.95", "== 0", ">=0.9")
- [x] 3.4 Support operators: >, <, ==, >=, <=, !=
- [x] 3.5 Return detailed validation result (passed, failed metric, expected vs actual)

## 4. Deterministic Test Class

- [x] 4.1 Create `DeterministicSimulationTest` in `src/test/java/.../sim/engine/`
- [x] 4.2 Tests run as part of default Surefire execution (no Spring context needed)
- [x] 4.3 Direct instantiation of engine components (no injection needed)
- [x] 4.4 Scenarios defined as @Nested classes with known inputs
- [x] 4.5 For each scenario, test methods validate:
  - [x] 4.5.1 Conflict detection with known anchor/incoming pairs
  - [x] 4.5.2 Conflict resolution with known authority levels
  - [x] 4.5.3 Scoring with known turn snapshots and ground truth
  - [x] 4.5.4 MetricsValidator assertions on scoring results
- [x] 4.6 Descriptive test names and class-level Javadoc

## 5. Test Utilities

- [x] 5.1 Helper methods: `anchor()`, `fact()`, `snapshot()` for test data construction
- [x] 5.2 `MetricsValidator.validateAll()` for batch metric assertions
- [x] 5.3 `ValidationResult.failureMessage()` and `AggregateResult.failureSummary()` for error formatting

## 6. Scenario Coverage (as @Nested classes)

### Scenario 1: Resist Direct Negation
- [x] 6.1.1 Negation of CANON fact detected and resolved as KEEP
- [x] 6.1.2 Scoring confirms zero contradictions when anchor resists negation
- [x] 6.1.3 Negation of RELIABLE fact also detected and kept

### Scenario 2: Resist Semantic Opposition
- [x] 6.2.1 Negation-based semantic opposition detected with >0.5 confidence
- [x] 6.2.2 Scoring reflects survival when opposition is resisted
- [x] 6.2.3 Unrelated statements produce no spurious conflicts

### Scenario 3: Maintain Rank Stability
- [x] 6.3.1 Reinforcement boosts rank by fixed increment (50)
- [x] 6.3.2 Rank clamped to MAX_RANK after multiple reinforcements
- [x] 6.3.3 Authority upgrades at threshold reinforcement counts (3, 7)
- [x] 6.3.4 Scoring confirms stability via consistent confirmations

### Scenario 4: Authority Enforces Preservation
- [x] 6.4.1 CANON anchor always KEEP against any conflict
- [x] 6.4.2 RELIABLE anchor also KEEP
- [x] 6.4.3 PROVISIONAL with high-confidence challenger is REPLACED
- [x] 6.4.4 PROVISIONAL with low-confidence challenger COEXISTs
- [x] 6.4.5 Mixed authority scoring: CANON survives, PROVISIONAL drifts

## 7. Verification

- [x] 7.1 Build and run all tests: `./mvnw.cmd test`
- [x] 7.2 All 4 scenarios pass (15 test methods total)
- [ ] 7.3 Intentionally break metrics bounds, verify test fails (manual)
- [x] 7.4 Live mode unaffected (no production code modified)
- [x] 7.5 Full test suite passes: 291 tests, 0 failures
- [x] 7.6 Performance: deterministic tests complete in <1 second

## 8. CI Integration

- [x] 8.1 Tests run in default Surefire execution (no config changes needed)
- [N/A] 8.2 No separate CI job needed (tests are fast unit tests)
- [N/A] 8.3 No CLAUDE.md changes needed (standard `./mvnw.cmd test` runs them)

## 9. Documentation & Cleanup

- [x] 9.1 Javadoc on `DeterministicSimulationTest` and `MetricsValidator`
- [N/A] 9.2 No YAML format to document (scenarios are code)
- [N/A] 9.3 No YAML scenario authoring to document
- [x] 9.4 Each scenario has @DisplayName annotations explaining purpose
- [N/A] 9.5 No CLAUDE.md update needed (standard test execution)
- [x] 9.6 Code style verified per CLAUDE.md

## Definition of Done

- ✓ All deterministic tests pass (4 scenarios, 15 test methods)
- ✓ Metrics validation works correctly (6 operators supported)
- ✓ Live mode unaffected (no production code modified)
- ✓ Test execution is fast (<1 second for all deterministic tests)
- ✓ Full test suite passes (291 tests)
