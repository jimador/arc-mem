## Why

The repository now has the correct two-module build split, but the internal package structure still reflects the old monolith. `arcmem-core` still contains the duplicated `dev.dunnam.arcmem.arcmem` namespace and simulator-named seam types, while `arcmem-simulator` still mixes bootstrap, chat, D&D schema, engine, reporting, and Vaadin UI under broad top-level packages. That makes ownership harder to read, keeps architectural seams implicit, and raises the cost of future refactors because package names do not match the responsibilities established by the module split.

## What Changes

- Repackage `arcmem-core` into domain-oriented packages so ARC-Mem memory, trust, conflict, maintenance, attention, assembly, extraction, persistence, prompt, and configuration concerns have explicit homes.
- Repackage `arcmem-simulator` into bounded simulator packages so bootstrap, config, chat, D&D schema, engine, adversary logic, scenarios, assertions, history, reporting, benchmarking, and UI are clearly separated.
- Remove legacy package duplication such as `dev.dunnam.arcmem.arcmem`.
- Move cross-module seam types out of simulator-named packages in core and into core-owned SPI or boundary packages.
- Preserve runtime behavior and module dependency direction while changing package names, file locations, imports, and Spring scanning.

## Capabilities

### New Capabilities
- `package-topology`: Define the required internal package structure and ownership rules for `arcmem-core` and `arcmem-simulator` after the module split.

### Modified Capabilities
- `module-topology`: Refine the post-split topology so package names and internal class placement match the established module ownership boundary.
- `developer-documentation-suite`: Require architecture documentation to reflect the canonical package layout and the cleanup of legacy package names after the module split.

## Impact

- Affected code areas: `arcmem-core/src/main/java/**`, `arcmem-simulator/src/main/java/**`, module-local tests, and package-sensitive resource/config wiring.
- Affected behavior surface: Spring component scanning, configuration discovery, package-local imports, and any code relying on package names for organization or resource lookup.
- Affected spec domains: [module-topology](../../specs/module-topology/spec.md), [developer-documentation-suite](../../specs/developer-documentation-suite/spec.md).

## Constitutional Alignment

- This change strengthens the architectural boundary already established by the two-module split instead of redefining runtime product behavior.
- This change keeps DICE external and keeps the simulator as the harness layer above ARC-Mem core.
- This change prioritizes clear ownership and maintainability over preserving historically convenient but misleading package names.

## Specification Overrides

- None.
