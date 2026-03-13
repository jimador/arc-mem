## Why

The current terminology overloads `unit` and keeps the older `ContextUnit` name even though the codebase now has a clearer conceptual split available. There are really two concepts:

- extracted semantic content before promotion
- managed ARC-Mem objects after promotion into bounded working memory

Using `SemanticUnit` for extracted content and `MemoryUnit` for promoted ARC-Mem state is easier for humans to reason about than continuing to overload `Unit` and `ContextUnit`. The rename also aligns the public Java model, UI text, and documentation with the actual pipeline semantics.

## What Changes

- Rename the promoted ARC-Mem model from `ContextUnit` to `MemoryUnit` across code, tests, UI, and docs.
- Rename related public types that are specifically about promoted ARC-Mem state to `MemoryUnit*`.
- Rename generic semantic-content concepts that are upstream or cross-stage to `SemanticUnit*` where that is more accurate than `Unit*`.
- Update documentation, prompts, and user-facing text to distinguish semantic units from memory units consistently.

## Capabilities

### New Capabilities
- `memory-and-semantic-unit-terminology`: Define the canonical terminology split between extracted semantic units and promoted memory units across code and docs.

### Modified Capabilities
- `arc-mem-terminology`: Replace the older context-unit terminology mapping with the new `SemanticUnit` / `MemoryUnit` distinction.
- `developer-documentation-suite`: Require architecture and developer docs to describe the pipeline using semantic-unit extraction and memory-unit lifecycle language.
- `package-topology`: Require package and type names to reflect the new terminology where public API and owning package names expose the old vocabulary.

## Impact

- Affected code areas: `arcmem-core/src/main/java/**`, `arcmem-simulator/src/main/java/**`, corresponding tests, prompt templates, and developer docs.
- Affected behavior surface: public Java type names, Spring wiring references, UI labels, OpenSpec terminology, and developer-facing architecture docs.
- Affected spec domains: [arc-mem-terminology](../../specs/arc-mem-terminology/spec.md), [developer-documentation-suite](../../specs/developer-documentation-suite/spec.md), [module-topology](../../specs/module-topology/spec.md).

## Constitutional Alignment

- This change clarifies the domain language without altering the two-module architecture or the ARC-Mem execution model.
- This change strengthens maintainability by making the extraction-to-memory pipeline understandable from names alone.
- This change keeps DICE external and preserves ARC-Mem as the implementation layer above it.

## Specification Overrides

- The current `arc-mem-terminology` spec preserves `ContextUnit` and `ContextUnitRepository` as Java type names. This change intentionally overrides that mapping so public Java names match the new `SemanticUnit` / `MemoryUnit` model.
