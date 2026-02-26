## Why

The `dice-anchors.jinja` system prompt contains blanket anti-revision instructions ("NEVER contradict established facts", "Do not accept user-requested retcons") that apply uniformly to all anchors regardless of authority level. This means the LLM refuses revision requests even for PROVISIONAL anchors — the lowest-trust tier. F01 (revision-intent-classification) added engine-level `ConflictType` classification and `RevisionAwareConflictResolver`, but the LLM never generates a revised assertion because the prompt forbids it. The revision is blocked upstream before extraction can produce a contradicting proposition for the conflict gate to process.

## What Changes

- Add `[revisable]` annotation to PROVISIONAL and UNRELIABLE anchor entries in the tiered compliance template
- Add revision-carveout instructions to the Critical Instructions section explaining when the LLM MAY accept a user revision
- Conditionally annotate RELIABLE anchors as `[revisable]` when `anchor.revision.reliable-revisable = true`
- Pass `revision_enabled` and `reliable_revisable` flags as template variables from `AnchorsLlmReference`
- Update the Verification Protocol to account for revision-eligible facts
- Ensure CANON anchors are never annotated as revisable

## Capabilities

### New Capabilities

### Modified Capabilities
- `authority-tiered-compliance`: Add revision carveout language and `[revisable]` annotations to the tiered compliance template for PROVISIONAL/UNRELIABLE anchors

## Impact

- **Prompt template**: `dice-anchors.jinja` — revision carveout in Critical Instructions, `[revisable]` annotations in tiered anchor blocks, updated Verification Protocol (~100-150 additional tokens)
- **Assembly**: `AnchorsLlmReference` — pass `revision_enabled` and `reliable_revisable` template variables
- **Configuration**: No new properties — reuses `anchor.revision.enabled` and `anchor.revision.reliable-revisable` from F01
- **Risk**: Overly permissive carveout language could weaken drift resistance. Mitigated by restricting carveout to PROVISIONAL/UNRELIABLE tiers and requiring explicit revision-intent language from the user
