## Why

The conflict detection system treats all contradictions uniformly — a player saying "Actually, make Anakin a bard instead of a wizard" is handled identically to adversarial drift attempting to corrupt established facts. This causes legitimate revisions to harden the disputed anchor (reinforcement loop on DM refusal) while the system blocks the user from correcting their own input. No surveyed AI memory framework (Letta, Graphiti, Mem0, LangMem, MemOS) distinguishes revision from contradiction, making this a genuinely novel capability. The `Conflict` record currently has no type field, the `conflict-detection.jinja` prompt returns only a boolean `contradicts`, and `AuthorityConflictResolver` applies the same resolution matrix regardless of user intent.

## What Changes

- Add a `ConflictType` enum (`REVISION`, `CONTRADICTION`, `WORLD_PROGRESSION`) to classify detected conflicts by intent
- Extend the `Conflict` record with a `ConflictType` field (additive — existing constructors preserved via defaults)
- Extend `conflict-detection.jinja` to return `conflictType` and `reasoning` (chain-of-thought) alongside the existing `contradicts` boolean — no additional LLM call
- Extend `batch-conflict-detection.jinja` with per-candidate `conflictType` in the result schema
- Extend `LlmConflictDetector.parseResponse()` to read `conflictType` from JSON (absent/null defaults to `CONTRADICTION` — fail-closed)
- Create `RevisionAwareConflictResolver` that dispatches on `ConflictType` before delegating to `AuthorityConflictResolver` for contradiction cases
- Gate revision acceptance on authority eligibility: PROVISIONAL/UNRELIABLE revisable by default, RELIABLE configurable, CANON never
- Add `SupersessionReason.USER_REVISION` to distinguish revision-triggered supersession from conflict replacement
- Add configuration properties for revision enablement, authority gating, and confidence thresholds
- Log `ConflictType` classification and reasoning in OTEL spans and `TrustAuditRecord`

## Capabilities

### New Capabilities
- `revision-intent-classification`: ConflictType enum, LLM prompt classification (extending conflict-detection templates), and RevisionAwareConflictResolver routing logic with authority-gated revision acceptance and observability

### Modified Capabilities
- `conflict-detection`: Conflict record gains a `ConflictType` field; prompt templates return classification alongside the existing boolean; `parseResponse()` reads the new field
- `anchor-supersession`: `SupersessionReason` gains `USER_REVISION` value for revision-triggered supersession

## Impact

- **Prompt templates**: `conflict-detection.jinja`, `batch-conflict-detection.jinja` — extended JSON schema (~200-400 additional tokens for few-shot examples)
- **Conflict detection**: `ConflictDetector.Conflict` record, `LlmConflictDetector` — additive field and parse changes
- **Conflict resolution**: New `RevisionAwareConflictResolver` wired as primary resolver, delegating to `AuthorityConflictResolver`
- **Engine**: `AnchorEngine` — new `SupersessionReason.USER_REVISION` value; supersede path triggered by revision classification
- **Configuration**: `DiceAnchorsProperties` — new `anchor.revision.*` properties
- **Observability**: OTEL span attributes, `TrustAuditRecord` — classification decision trail
- **Lexical path**: `NegationConflictDetector` conflicts default to `CONTRADICTION` (negation patterns never indicate revision intent) — no changes needed
- **Risk**: False-positive REVISION classification could allow adversarial drift through the revision gate. Mitigated by fail-closed default, separate confidence threshold (0.75), and CANON immunity
