## 1. Build Topology

- [x] 1.1 Convert the root `pom.xml` into an aggregator parent that declares `arcmem-core` and `arcmem-simulator`.
- [x] 1.2 Create `arcmem-core/pom.xml` with ARC-Mem and DICE-facing dependencies required by the core module.
- [x] 1.3 Create `arcmem-simulator/pom.xml` with Spring Boot, Vaadin, and simulator-facing dependencies, including a dependency on `arcmem-core`.
- [x] 1.4 Verify the module dependency graph satisfies the `module-topology` requirement that `arcmem-simulator` depends on `arcmem-core` and no first-party `dice` module exists.

## 2. Simulator Extraction

- [x] 2.1 Move Spring Boot bootstrap classes and simulator configuration classes into `arcmem-simulator`.
- [x] 2.2 Move `chat.*`, `sim.*`, and D&D schema types from `domain.*` into `arcmem-simulator` with minimal package renaming.
- [x] 2.3 Move simulator-owned resources, including D&D prompts and scenario YAML files, into `arcmem-simulator`.
- [x] 2.4 Verify the simulator still owns chat UI, scenario execution, adversary logic, benchmark flows, and report generation per the `module-topology` spec.

## 3. Core Extraction

- [x] 3.1 Move ARC-Mem implementation packages (`arcmem.*`, `assembly.*`, `extract.*`, `prompt.*`) into `arcmem-core`.
- [x] 3.2 Split mixed ownership persistence code so ARC-Mem storage semantics remain in `arcmem-core` and simulator run-history concerns move to `arcmem-simulator`.
- [x] 3.3 Resolve cross-module imports and Spring wiring so `arcmem-core` does not depend on `arcmem-simulator` or simulator-only D&D assets.
- [x] 3.4 Verify the first pass compiles without requiring the full internal package cleanup mandated by later refactors.

## 4. Verification

- [x] 4.1 Move tests into their owning modules and update module-local test execution as needed.
- [x] 4.2 Run module build and test verification to confirm the two-module repository compiles and the simulator harness remains runnable.
- [x] 4.3 Verify prompt and resource loading paths still resolve correctly after the module split.
- [x] 4.4 Verify behavior remains equivalent for the initial migration pass before starting any deeper package redesign.

## 5. Documentation Sync

- [x] 5.1 Update developer architecture documentation to describe the `arcmem-core` and `arcmem-simulator` ownership boundary.
- [x] 5.2 Update developer documentation to state that DICE remains an external dependency and is not introduced as a first-party module.
- [x] 5.3 Link the updated architecture documentation to this change's `module-topology` and `developer-documentation-suite` specs for traceability.
