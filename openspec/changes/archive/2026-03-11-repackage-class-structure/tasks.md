## 1. Core Namespace And Seam Cleanup

- [x] 1.1 Move `arcmem-core` root configuration types into `dev.dunnam.arcmem.core.config`.
- [x] 1.2 Repackage core seam types out of simulator-named packages into `dev.dunnam.arcmem.core.spi.*`.
- [x] 1.3 Verify no production package in `arcmem-core` still relies on legacy bucket semantics as its canonical structure.

## 2. Core Memory Subdomain Split

- [x] 2.1 Move model classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.model`.
- [x] 2.2 Move engine classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.engine`.
- [x] 2.3 Move trust classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.trust`.
- [x] 2.4 Move conflict classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.conflict`.
- [x] 2.5 Move maintenance classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.maintenance`.
- [x] 2.6 Move canon and invariant classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.canon`.
- [x] 2.7 Move budget strategy classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.budget`.
- [x] 2.8 Move mutation and reinforcement classes from the scratch inventory into `dev.dunnam.arcmem.core.memory.mutation`.
- [x] 2.9 Keep attention classes under `dev.dunnam.arcmem.core.memory.attention`.
- [x] 2.10 Keep lifecycle event classes under `dev.dunnam.arcmem.core.memory.event`.

## 3. Core Assembly Split

- [x] 3.1 Move retrieval and scoring classes into `dev.dunnam.arcmem.core.assembly.retrieval`.
- [x] 3.2 Move compaction and summary classes into `dev.dunnam.arcmem.core.assembly.compaction`.
- [x] 3.3 Move compliance and invariant-enforcement classes into `dev.dunnam.arcmem.core.assembly.compliance`.
- [x] 3.4 Move protected-content and constraint classes into `dev.dunnam.arcmem.core.assembly.protection`.
- [x] 3.5 Move prompt-token-budget and decoding-enforcement classes into `dev.dunnam.arcmem.core.assembly.budget`.

## 4. Simulator Package Cleanup

- [x] 4.1 Move simulator bootstrap classes into `dev.dunnam.arcmem.simulator.bootstrap` and simulator configuration into `dev.dunnam.arcmem.simulator.config`.
- [x] 4.2 Repackage chat classes into `dev.dunnam.arcmem.simulator.chat.*`.
- [x] 4.3 Repackage D&D schema types into `dev.dunnam.arcmem.simulator.domain.dnd`.
- [x] 4.4 Repackage simulator engine, scenario, adversary, assertions, benchmark, history, report, and UI classes into canonical simulator package roots.
- [x] 4.5 Split simulator UI into bounded `ui` areas where that improves ownership clarity.

## 5. Tests And Wiring

- [x] 5.1 Update `arcmem-core` tests to follow the new subdomain package declarations and directory structure.
- [x] 5.2 Update `arcmem-simulator` tests to follow the new owning-module package declarations and directory structure.
- [x] 5.3 Update imports, Spring wiring, and package-local references for the remaining core subpackage moves.
- [x] 5.4 Verify prompt, frontend, and resource loading still resolve after the remaining package changes.
- [x] 5.5 Run compile and targeted test verification for each completed move group before marking it done.
- [x] 5.6 Run full-repo verification after all remaining package moves are complete.

## 6. Documentation Sync

- [x] 6.1 Update architecture documentation to reflect the real final core subdomain packages, not only canonical roots.
- [x] 6.2 Update remaining developer docs that still describe `core.memory` and `core.assembly` as flat packages.
- [x] 6.3 Verify documentation traces to the updated `package-topology`, `module-topology`, and `developer-documentation-suite` specs.
