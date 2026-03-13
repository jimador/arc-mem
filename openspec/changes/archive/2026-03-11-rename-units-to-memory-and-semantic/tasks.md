## 1. Artifact And Type Rename

- [x] 1.1 Rename `ContextUnit` to `MemoryUnit` in `arcmem-core` production code.
- [x] 1.2 Rename `ContextUnit*` support types to `MemoryUnit*` where they specifically model promoted ARC-Mem state.
- [x] 1.3 Rename generic `Unit*` helper types to `SemanticUnit*` or `MemoryUnit*` based on the design mapping.

## 2. Test And Wiring Update

- [x] 2.1 Rewrite imports, references, mocks, and fixtures in `arcmem-core` tests for the renamed types.
- [x] 2.2 Rewrite imports, references, mocks, and fixtures in `arcmem-simulator` tests and production code for the renamed types.
- [x] 2.3 Verify Spring wiring, component scanning, and resource loading after the rename.

## 3. Docs And UX Language

- [x] 3.1 Update architecture docs, developer docs, and OpenSpec text to distinguish semantic units from memory units.
- [x] 3.2 Update prompt templates and user-facing UI labels to use the renamed terminology where appropriate.

## 4. Verification

- [x] 4.1 Run compile/test verification for `arcmem-core`.
- [x] 4.2 Run full-repo verification.
- [x] 4.3 Update the OpenSpec task list to reflect completed work.
