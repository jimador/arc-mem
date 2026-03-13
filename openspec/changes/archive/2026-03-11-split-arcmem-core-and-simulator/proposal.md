## Why

The current single-module layout mixes ARC-Mem implementation concerns with simulator harness concerns, which makes package boundaries unclear and makes it harder to evolve ARC-Mem as a reusable layer on top of DICE. This change is needed now because the current package structure is already obscuring ownership between the ARC-Mem implementation, the D&D-driven simulator, and the chat and experiment surfaces that exist only to stress-test drift behavior.

## What Changes

- Split the build into `arcmem-core` and `arcmem-simulator` Maven modules.
- Move ARC-Mem implementation code into `arcmem-core`, including memory, trust, conflict, maintenance, assembly, extraction, and core persistence logic.
- Move simulator harness code into `arcmem-simulator`, including D&D schema, scenarios, adversary logic, chat UI, experiment engine, reports, and simulator UI.
- Define an explicit dependency boundary where `arcmem-simulator` depends on `arcmem-core`, and `arcmem-core` depends on DICE as an external library.
- **BREAKING** Replace the current single-module packaging and package layout with a two-module topology and new package roots aligned to module ownership.
- Preserve current simulator behavior during the initial split; implementation-neutral behavioral requirements remain unchanged unless explicitly captured in delta specs.

## Capabilities

### New Capabilities
- `module-topology`: Define the required two-module build structure, ownership boundaries, and dependency direction between ARC-Mem core and the simulator harness.

### Modified Capabilities
- `developer-documentation-suite`: Update developer-facing documentation requirements so architecture and repo docs reflect the new module topology and ownership boundaries.

## Impact

- Affected build and repo structure: root `pom.xml`, new child module `pom.xml` files, source tree layout, test layout, and resource placement.
- Affected code areas: current `dev.dunnam.arcmem.arcmem`, `assembly`, `extract`, `persistence`, `prompt`, `chat`, `domain`, and `sim` packages.
- Affected systems: Spring component scanning, resource loading, packaging, and module-level test execution.
- Affected spec domains: [developer-documentation-suite](../../specs/developer-documentation-suite/spec.md).

## Constitutional Alignment

- This change improves clarity of ownership and reduces accidental coupling between reusable ARC-Mem implementation code and simulator-only harness code.
- This change preserves the current simulator-driven validation model rather than redefining runtime behavior during the structural split.
- This change keeps DICE as an external dependency and does not duplicate upstream ownership inside this repository.

## Specification Overrides

- None.
