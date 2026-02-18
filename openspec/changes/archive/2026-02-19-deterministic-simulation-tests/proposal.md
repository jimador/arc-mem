## Why

Simulation tests depend on live LLM calls, making them flaky, slow, and expensive in CI. Deterministic tests use canned LLM responses loaded from YAML, enabling fast, repeatable, cost-free validation that anchor mechanics work. Scenario files can be reused for both live demos and deterministic CI.

## What Changes

- Add `deterministic-sim.yaml` with canned-response scenarios (same structure as live scenarios)
- Update `SimulationTurnExecutor` to detect "canned" scenario type and return prepared responses instead of calling LLM
- Add deterministic test class that runs scenarios, validates metrics
- Live scenarios remain unchanged; existing tests continue to work
- No breaking changes; new capability

## Capabilities

### New Capabilities
- `deterministic-simulation-tests`: YAML-based canned-response scenario testing for CI reliability

### Modified Capabilities
- `simulation`: Scenario execution now supports both live and deterministic modes

## Impact

- **Files**: New `src/main/resources/simulations/deterministic-sim.yaml`, update `sim/engine/SimulationTurnExecutor.java`, new test class `src/test/java/.../sim/DeterministicSimulationTests.java`
- **APIs**: `SimulationTurnExecutor.executeTurn()` behavior unchanged; mode detection added internally
- **Config**: Optional `simulations.deterministic-enabled` property (default: true)
- **Affected**: Simulation harness, test suite
- **Value**: CI reliability, cost-free test coverage, reusable scenarios

## Constitutional Alignment

- RFC 2119 keywords: Canned scenarios MUST define expected metrics, tests SHOULD validate them
- Single-module Maven project: Changes in sim/engine and test packages
