## Why

The anchor promotion pipeline evaluates propositions for trust (source authority, extraction confidence, graph consistency, corroboration) but has no signal for proposition *value* -- whether a proposition adds meaningful new information or is relevant to the current conversation. Low-novelty propositions (restating known facts) and low-importance propositions (tangential details) consume anchor budget without improving attention stability. Sleeping-llm's curator (Amygdala) demonstrates that two-axis scoring -- novelty and importance -- effectively filters low-value facts before they enter working memory. Adding these signals as tie-breakers in the existing `TrustPipeline` improves anchor budget utilization without disrupting proven trust evaluation behavior.

## What Changes

- **Add `NoveltySignal`** implementing `TrustSignal` -- scores propositions by information gain relative to existing anchors using Jaccard similarity on tokenized text. Low similarity to existing anchors = high novelty.
- **Add `ImportanceSignal`** implementing `TrustSignal` -- scores propositions by keyword/entity overlap with recent conversation context. High overlap with active discussion topics = high importance.
- **Update `DomainProfile` built-in profiles** (NARRATIVE, SECURE, BALANCED) -- redistribute weights to include `novelty` and `importance` at low default weights (~0.05 each) so quality signals act as tie-breakers, not dominators.
- **Add quality scoring configuration** to `DiceAnchorsProperties` -- opt-in toggle (default off), configurable thresholds for Jaccard window size and stop-word filtering.
- **Register signals in `TrustConfiguration`** -- conditional bean registration gated on the quality scoring toggle.
- **Add unit tests** for scoring algorithms and signal integration.

## Capabilities

### New Capabilities
- `proposition-quality-scoring`: Two-axis quality scoring (novelty + importance) for proposition evaluation via `TrustSignal` implementations, with heuristic scoring algorithms (Jaccard similarity, keyword overlap), configurable weights per `DomainProfile`, and opt-in activation.

### Modified Capabilities
- `anchor-trust`: DomainProfile built-in profiles updated with weights for two new quality signals; TrustConfiguration registers conditional quality signal beans.

## Impact

- **Files**: New `anchor/NoveltySignal.java`, `anchor/ImportanceSignal.java`. Modified `anchor/DomainProfile.java` (profile weights), `anchor/TrustConfiguration.java` (bean registration), `DiceAnchorsProperties.java` (config). New tests.
- **APIs**: No breaking changes. Two new `TrustSignal` implementations participate in existing `TrustPipeline`. Absent-signal weight redistribution (already in `TrustEvaluator`) handles disabled state transparently.
- **Config**: New `dice-anchors.anchor.quality-scoring.enabled` (default: false). New thresholds for scoring tuning.
- **Performance**: Zero LLM cost. Jaccard similarity is O(n*m) where n = anchor count, m = average token count. Computed inline during trust evaluation.
- **Dependencies**: No new external dependencies.

## Constitutional Alignment

- **RFC 2119**: All requirements use normative keywords per Article I.
- **Neo4j sole persistence (Art. II)**: No persistence changes. Scoring is computed in-memory from existing `PropositionNode` and `Anchor` data.
- **Constructor injection (Art. III)**: All new beans use constructor injection.
- **Records for immutable data (Art. IV)**: Configuration uses existing record structure in `DiceAnchorsProperties`.
- **Anchor invariants (Art. V)**: Quality signals do not modify rank, authority, or budget. They contribute to the composite trust score which feeds existing promotion zone routing.
- **Test-first (Art. VII)**: Tests for scoring algorithms and integration specified.
