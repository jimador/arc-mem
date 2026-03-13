## Why

The project now owns the `arcmem.dev` domain. The current package root `dev.dunnam.arcmem` should be `dev.arcmem` to match the domain and establish the project's own namespace independent of any personal developer identity.

## What Changes

- **BREAKING**: All Java packages renamed from `dev.dunnam.arcmem.*` to `dev.arcmem.*` across both modules (arcmem-core, arcmem-simulator)
- **BREAKING**: Maven `groupId` updated from `dev.dunnam.arcmem` to `dev.arcmem`
- POM `mainClass` reference updated
- All `package` declarations and `import` statements updated
- Directory structure moved from `dev/dunnam/arcmem/` to `dev/arcmem/`

## Capabilities

### New Capabilities

_None — this is a mechanical rename with no new functionality._

### Modified Capabilities

_None — no spec-level behavior changes. All requirements remain identical; only the Java package namespace changes._

## Impact

- **314 Java source files** (174 core + 140 simulator) — package declarations and cross-module imports
- **Test files** — same package rename in all test directories
- **POM files** — `groupId` and `mainClass` references
- **Zero runtime behavior change** — purely structural
- **Downstream consumers** — any external code importing `dev.dunnam.arcmem.*` would break (none known)
