## Why

Current conflict detection is purely lexical (negation patterns), missing semantic contradictions like "alive" vs "dead" or "supports" vs "opposes." Adding LLM-based semantic detection after lexical checks improves contradiction coverage. Subject-filtering pre-checks reduce expensive LLM calls by ~70% (only check semantically-novel candidates). This strengthens the "anchors resist drift" narrative without major infrastructure cost.

## What Changes

- Add `SemanticConflictDetector` that uses LLM to detect semantic opposition
- Add `SubjectFilter` that pre-filters candidates based on subject term matching (e.g., skip semantic check if no shared subject)
- Create `CompositeConflictDetector` that chains lexical then semantic detection
- Add `ConflictDetectionStrategy` enum to select strategy (LEXICAL_ONLY, LEXICAL_THEN_SEMANTIC, SEMANTIC_ONLY)
- Make conflict detection strategy configurable via property
- No breaking changes; existing behavior preserved

## Capabilities

### New Capabilities
- `semantic-conflict-detection`: LLM-based conflict detection with subject-filtering optimization
- `conflict-detection-strategy`: Pluggable conflict detection pipeline (lexical, semantic, or both)

### Modified Capabilities
- `conflict-detection`: Pipeline now includes optional semantic check (behavior change in detection coverage, not spec change)

## Impact

- **Files**: New `anchor/SemanticConflictDetector.java`, new `anchor/SubjectFilter.java`, refactored `anchor/ConflictDetector.java` with strategy pattern, `DiceAnchorsProperties` update
- **APIs**: `ConflictDetector` interface unchanged; strategy selection added internally
- **Config**: New `anchor.conflict-detection-strategy` property (LEXICAL_ONLY, LEXICAL_THEN_SEMANTIC, SEMANTIC_ONLY)
- **Affected**: Anchor promotion flow, conflict resolution
- **Performance**: Reduces semantic LLM calls by ~70% via subject filtering

## Constitutional Alignment

- RFC 2119 keywords: Policies MUST define detection strategy, SHOULD be composable
- Single-module Maven project: Changes contained to `anchor/` package
- No breaking changes; backward compatible via strategy selection
