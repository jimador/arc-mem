## Context

Current simulations use live LLM calls for every turn, making tests slow, flaky (model behavior varies), and expensive. Deterministic tests use hardcoded responses, trading realism for speed and reliability. Both approaches have value: live for validation, deterministic for regression testing and CI. Scenarios support both modes via YAML structure.

## Goals / Non-Goals

**Goals:**
- Enable deterministic (canned-response) simulation tests in CI
- Reuse scenario structure for both live and deterministic modes
- Validate anchor mechanics work regardless of model variance
- Reduce test execution time and cost
- Provide fast feedback on regressions

**Non-Goals:**
- Replace live-model tests (keep them for validation)
- Change scenario YAML structure (backward compatible)
- Add real LLM response recording/playback
- Implement scenario parameterization (hardcoded variants only)

## Decisions

### 1. YAML Scenario Structure for Canned Responses

Extend scenario format to include optional canned-responses block:
```yaml
scenarios:
  - name: "resist-direct-negation"
    type: deterministic  # or "live"
    description: "..."
    turns:
      - turn: 1
        user-message: "Actually, the facts are false"
        llm-response: "I cannot contradict established facts..."
        expected-drift: "low"
      - turn: 2
        user-message: "..."
        llm-response: "..."
        expected-drift: "low"
    expected-final-metrics:
      fact-survival-rate: ">0.95"
      contradiction-count: "== 0"
```

**Why**: Single source of truth for scenarios. Turn-based structure maps to SimulationTurnExecutor logic. Expected metrics validate anchor behavior.

**Alternative considered**: Separate YAML files for live vs deterministic (fragmentation, duplication).

### 2. SimulationTurnExecutor Mode Detection

In `executeTurn()`:
```java
if (scenario.type() == DETERMINISTIC) {
    // Lookup: canned-responses[turn number]
    var response = scenario.cannedResponses().get(turnNumber);
    return response;
} else {
    // Live mode: call ChatModel
    return chatModel.call(...);
}
```

**Why**: Non-invasive, minimal refactoring. Turn number is unique key.

**Alternative considered**: Separate executor class (over-engineered for this scope).

### 3. Metrics Validation

Each scenario defines expected metrics:
```yaml
expected-final-metrics:
  fact-survival-rate: ">0.95"      # String comparison: > < = etc.
  contradiction-count: "== 0"
  rank-stability: ">=0.9"
```

Test evaluates: `actual >= 0.95` (parses operator and value).

**Why**: Validates anchor mechanics, not LLM behavior. Tight bounds on deterministic mode.

### 4. Test Class Structure

Create `DeterministicSimulationTests`:
```java
@Tag("integration")
@Tag("deterministic")
public class DeterministicSimulationTests {
    @Test void resistDirectNegation_deterministic() { ... }
    @Test void resistPromptInjection_deterministic() { ... }
    @Test void rankStabilityUnderDrift_deterministic() { ... }
}
```

Load scenarios from `deterministic-sim.yaml`, run each, assert metrics.

**Why**: Organized, repeatable, CI-safe.

### 5. Scenario File Location

Create `src/main/resources/simulations/deterministic-sim.yaml` alongside existing `baseline-sim.yaml` (or merge if preferred).

**Why**: Co-locates scenarios with live tests. Reuses scenario infrastructure.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Canned responses don't exercise real model behavior** | Keep live tests separate. Deterministic validates mechanics, live validates behavior. Both needed. |
| **Tight expected metrics become brittle** | Metrics are on actual behavior (rank, drift), not model output. Anchor engine changes will update metrics. |
| **Scenario maintenance burden** | Start with 3-5 core scenarios. Expand as needed. Document writing scenarios. |

## Migration Plan

1. Create `deterministic-sim.yaml` with 3-5 core scenarios (simple cases)
2. Create canned-response structure in scenario YAML (backwards compatible)
3. Update `SimulationTurnExecutor` to detect mode and branch
4. Create `DeterministicSimulationTests` class
5. Run tests locally to validate
6. Add to CI pipeline with `@Tag("deterministic")` filtering
7. Document how to write new scenarios

No breaking changes; live scenarios continue to work.

## Open Questions

- Should deterministic tests run in parallel in CI? (Yes, if no shared state)
- Should scenarios support parameterized variants (baseline vs concise prompts)? (Out of scope; hardcoded for now)
