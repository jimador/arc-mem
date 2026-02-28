## Context

dice-anchors uses Embabel Agent 0.3.5-SNAPSHOT for chat orchestration. Research (R01) found 52 Embabel imports across 30 Java files spanning 10 packages. The project uses 4 core annotations (`@EmbabelComponent`, `@Action`, `@MatryoshkaTools`, `@LlmTool`) but leaves 8 annotation parameters and 3 additional annotations (`@AchievesGoal`, `@Agent`, `@Export`) unused. No single document captures this integration surface, forcing repeated discovery work.

This is a documentation-only feature. No code changes.

## Goals / Non-Goals

**Goals:**
- Create a single reference document cataloging Embabel integration surface
- Document current usage with verifiable file:line references
- Document available-but-unused capabilities with applicability context
- Explain CQS tool restructuring rationale
- Link from DEVELOPING.md for contributor discoverability

**Non-Goals:**
- Implement any of the identified opportunities (deferred to F2-F4)
- Document DICE-specific APIs (covered separately in F3)
- Cover deprecated Embabel patterns
- Generate API documentation from source (this is a curated human-readable reference)

## Decisions

### 1. Document Location: `docs/dev/embabel-api-inventory.md`

Place the inventory under `docs/dev/` to signal it is a developer reference, not user-facing documentation.

**Why**: Separates development references from project-level docs. The `dev/` subdirectory groups implementation-oriented references together.

### 2. Document Structure

```
docs/dev/embabel-api-inventory.md
 1. Current Usage
    - Annotations (4) with file:line references
    - Interfaces and APIs (ActionContext, AiBuilder, Chatbot, etc.)
    - Patterns (template rendering, tool registration, event listeners)
 2. Available But Unused
    - Annotation parameters (8 unused across @Action, @MatryoshkaTools, @LlmTool)
    - Annotations (@AchievesGoal, @Agent, @Export)
    - Modes (GOAP chatbot, blackboard binding)
 3. Tool Organization
    - CQS principle and rationale
    - Read-only vs. full-access registration contexts
 4. Recommended Patterns
    - Tool description quality
    - Error handling in tools
    - Message vs. trigger pattern
```

**Why**: Four sections map to the four categories identified in the feature doc. Current usage first (most frequently consulted), unused capabilities second (opportunity catalog), tool organization third (directly informs F2), patterns last (reference material).

### 3. File:Line Reference Format

Use the format `ClassName.java:NN` with a brief context note. Example:
```
@EmbabelComponent -- ChatActions.java:45 (class-level, declares chat orchestration component)
```

**Why**: Compact, grep-friendly, and provides enough context to understand the usage without opening the file.

### 4. DEVELOPING.md Integration

Add a single line in DEVELOPING.md under a "Developer References" or equivalent section linking to the inventory.

**Why**: Minimal-touch integration. DEVELOPING.md is the natural entry point for contributors; a link ensures discoverability without restructuring existing documentation.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **File:line references drift** | Note that references are valid as of feature completion date. Encourage updating on major refactors. |
| **Inventory becomes stale** | Keep the document focused on stable integration points. Volatile details belong in code comments. |
| **Scope creep into implementation** | Spec explicitly requires documentation-only. No code changes in this feature. |

## Open Questions

None -- all decisions resolved in prep doc and research phase.
