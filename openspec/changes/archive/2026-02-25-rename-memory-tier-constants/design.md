## Context

`MemoryTier` is a three-value enum (`COLD`, `WARM`, `HOT`) that drives decay rates and eviction priority. The enum already uses short, self-documenting names.

CLAUDE.md's Key Design Decisions section (#15) documents:
> Memory tiers — `MemoryTier` classifies propositions as `T0_INVARIANT`, `T1_WORKING`, or `T2_EPISODIC`.

These names (`T0_INVARIANT`, `T1_WORKING`, `T2_EPISODIC`) do not exist in the codebase. They appear to be an earlier design that was abandoned before or during initial implementation. The enum has always been `COLD`, `WARM`, `HOT` in the committed code. `openspec/project.md` carries the same stale reference.

There are no code changes needed. The enum definition, all call sites, and the `openspec/specs/memory-tiering/spec.md` already use `COLD`/`WARM`/`HOT` correctly.

## Goals / Non-Goals

**Goals:**
- Remove stale `T0_INVARIANT`/`T1_WORKING`/`T2_EPISODIC` references from `CLAUDE.md` and `openspec/project.md`
- Ensure documentation is consistent with the actual enum values

**Non-Goals:**
- Renaming enum constants (already correct)
- Changing tier semantics, thresholds, or behavior
- Updating Neo4j migration strings (no T-prefixed strings exist in Cypher)
- Updating test files (tests already use `COLD`, `WARM`, `HOT`)

## Decisions

### Decision: Documentation-only change

**Chosen:** Update only `CLAUDE.md` and `openspec/project.md` to replace the stale T-prefixed names with the actual `COLD`/`WARM`/`HOT` names.

**Alternatives considered:**
- Rename the enum to match the proposal names (`INVARIANT`, `WORKING`, `EPISODIC`) — rejected because (1) the current names are already clear and self-documenting, (2) semantics don't match (`INVARIANT` implies unchanging content, but HOT/COLD/WARM reflects recency/rank dynamics, not content stability), (3) breaks all call sites for no benefit.

### Decision: No Neo4j migration needed

The `AnchorRepository` migration Cypher strings were cited in the proposal as containing `'T0_INVARIANT'` literals. Verification shows no such strings exist — the repository uses `'HOT'`, `'WARM'`, `'COLD'` in all migration Cypher. No migration is needed.

## Risks / Trade-offs

- **[Risk]** Future readers see the proposal.md referencing renames that aren't reflected in code → Mitigation: design.md (this document) clarifies the discrepancy; proposal.md is archived with the change.
- **[Risk]** CLAUDE.md is a large file; updating a single line could accidentally drop surrounding context → Mitigation: surgical Edit tool change, not a full rewrite.

## Migration Plan

1. Edit `CLAUDE.md` — replace stale line in Key Design Decisions #15
2. Edit `openspec/project.md` — replace stale line in Key Design Principles section
3. No compilation, test, or Neo4j migration steps needed
