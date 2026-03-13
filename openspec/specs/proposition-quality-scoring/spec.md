# Proposition Quality Scoring Specification

## ADDED Requirements

### Requirement: Novelty scoring via Jaccard similarity

The system SHALL compute a novelty score in [0.0, 1.0] for each proposition evaluated by the trust pipeline. Novelty measures information gain relative to existing active memory units in the same context.

The novelty score SHALL be computed as:
1. Tokenize the proposition text into a set of lowercase word tokens, excluding stop words.
2. For each active memory unit in the context, tokenize its text into the same form.
3. Compute the Jaccard similarity coefficient between the proposition token set and each memory unit token set: `|intersection| / |union|`.
4. Take the maximum Jaccard similarity across all memory units as `maxSimilarity`.
5. Novelty score = `1.0 - maxSimilarity`.

A proposition identical to an existing memory unit scores 0.0 (no novelty). A proposition sharing no tokens with any memory unit scores 1.0 (maximum novelty).

When no active memory units exist in the context, novelty SHALL be 1.0 (all propositions are novel in an empty context).

#### Scenario: Proposition identical to existing memory unit

- **GIVEN** an active memory unit with text "The dragon guards the eastern gate"
- **WHEN** a proposition "the dragon guards the eastern gate" is scored for novelty
- **THEN** the novelty score SHALL be 0.0 (after stop-word removal and lowercasing, token sets are identical)

#### Scenario: Proposition completely novel

- **GIVEN** active memory units about weather and geography
- **WHEN** a proposition "The artifact requires a blood sacrifice" is scored for novelty
- **THEN** the novelty score SHALL be 1.0 (no token overlap with existing memory units)

#### Scenario: Partial overlap

- **GIVEN** an active memory unit with text "The dragon breathes fire"
- **WHEN** a proposition "The dragon hoards gold" is scored for novelty
- **THEN** the novelty score SHALL be between 0.0 and 1.0 (partial token overlap on "dragon")

#### Scenario: Empty context yields maximum novelty

- **GIVEN** no active memory units exist in the context
- **WHEN** any proposition is scored for novelty
- **THEN** the novelty score SHALL be 1.0

### Requirement: Importance scoring via keyword overlap

The system SHALL compute an importance score in [0.0, 1.0] for each proposition evaluated by the trust pipeline. Importance measures relevance to the current conversation context.

The importance score SHALL be computed as:
1. Collect text from the proposition's source context -- the source IDs and any associated conversation text available on the `PropositionNode`.
2. Tokenize the proposition text into a set of lowercase word tokens, excluding stop words.
3. Tokenize the active memory unit texts (representing accumulated conversation context) into a combined token set.
4. Compute the overlap ratio: `|proposition tokens intersect context tokens| / |proposition tokens|`.
5. Importance score = overlap ratio, clamped to [0.0, 1.0].

A proposition whose keywords all appear in existing context scores 1.0 (highly relevant to ongoing discussion). A proposition with no keyword overlap scores 0.0 (tangential).

When no active memory units exist in the context, importance SHALL be 0.5 (neutral -- insufficient context to judge relevance).

#### Scenario: Proposition highly relevant to conversation

- **GIVEN** active memory units mentioning "dragon", "gate", "guardian", "eastern"
- **WHEN** a proposition "The eastern dragon guards the gate fiercely" is scored for importance
- **THEN** the importance score SHALL be high (most proposition keywords appear in context)

#### Scenario: Proposition tangential to conversation

- **GIVEN** active memory units about a dungeon and its traps
- **WHEN** a proposition "The weather in the capital is mild" is scored for importance
- **THEN** the importance score SHALL be low (minimal keyword overlap with context)

#### Scenario: Empty context yields neutral importance

- **GIVEN** no active memory units exist in the context
- **WHEN** any proposition is scored for importance
- **THEN** the importance score SHALL be 0.5

### Requirement: TrustSignal integration

`NoveltySignal` and `ImportanceSignal` SHALL implement the `TrustSignal` interface.

- `NoveltySignal.name()` SHALL return `"novelty"`.
- `ImportanceSignal.name()` SHALL return `"importance"`.
- Both signals SHALL return `OptionalDouble.empty()` when quality scoring is disabled via configuration, causing `TrustEvaluator` to redistribute their weight to other present signals.
- Both signals MUST be thread-safe per the `TrustSignal` contract.
- Both signals MUST NOT throw exceptions -- return `OptionalDouble.empty()` on any failure.

#### Scenario: Signals return scores when enabled

- **GIVEN** quality scoring is enabled in configuration
- **WHEN** a proposition is evaluated through the trust pipeline
- **THEN** the trust audit record SHALL contain entries for "novelty" and "importance" signals

#### Scenario: Signals return empty when disabled

- **GIVEN** quality scoring is disabled in configuration
- **WHEN** a proposition is evaluated through the trust pipeline
- **THEN** the novelty and importance signals SHALL return `OptionalDouble.empty()`
- **AND** their weight SHALL be redistributed to other present signals by `TrustEvaluator`

### Requirement: Stop-word filtering

Both novelty and importance scoring SHALL exclude common English stop words during tokenization to improve signal quality. The stop-word list SHALL include at minimum: "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to", "for", "of", "and", "or", "but", "not", "with", "by", "from", "that", "this", "it", "as", "be", "has", "had", "have".

The stop-word list SHALL be a static constant, not configurable.

#### Scenario: Stop words excluded from similarity computation

- **GIVEN** a memory unit "The dragon is in the cave"
- **WHEN** a proposition "A dragon was in a cave" is scored
- **THEN** stop words ("the", "is", "in", "a", "was") SHALL be excluded
- **AND** similarity SHALL be computed on content words ("dragon", "cave") only

### Requirement: Opt-in configuration

Quality scoring SHALL be controlled by a configuration toggle:
- Property: `arc-mem.unit.quality-scoring.enabled`
- Default: `false`
- When `false`, `NoveltySignal` and `ImportanceSignal` SHALL return `OptionalDouble.empty()` for all evaluations, effectively disabling scoring without removing beans from the pipeline.

#### Scenario: Quality scoring disabled by default

- **GIVEN** no explicit quality scoring configuration
- **WHEN** a proposition is evaluated through the trust pipeline
- **THEN** novelty and importance signals SHALL not contribute to the trust score

#### Scenario: Quality scoring enabled via configuration

- **GIVEN** `arc-mem.unit.quality-scoring.enabled=true`
- **WHEN** a proposition is evaluated through the trust pipeline
- **THEN** novelty and importance signals SHALL contribute to the trust score

### Requirement: Observability in trust audit

When quality scoring is enabled and signals produce values, the novelty and importance scores MUST appear in the `TrustAuditRecord.signalAudit` map alongside existing signal contributions (sourceAuthority, extractionConfidence, graphConsistency, corroboration).

#### Scenario: Quality scores in audit record

- **GIVEN** quality scoring is enabled
- **WHEN** a proposition is evaluated and produces novelty=0.8 and importance=0.6
- **THEN** the `TrustAuditRecord.signalAudit` map SHALL contain entries `{"novelty": 0.8, "importance": 0.6}` in addition to existing signal entries

## Invariants

- **QS1**: Quality scores MUST be in the range [0.0, 1.0]. Scores outside this range MUST be clamped.
- **QS2**: Quality scoring MUST NOT introduce LLM calls. All scoring is heuristic-based.
- **QS3**: Quality scoring MUST NOT modify proposition or memory unit state. Scoring is read-only.
- **QS4**: When quality scoring is disabled, the trust pipeline MUST produce identical results to a pipeline without quality signal beans registered. Absent-signal weight redistribution in `TrustEvaluator` guarantees this.
- **QS5**: Quality scoring is complementary to `DuplicateDetector`. A proposition may score low novelty and still pass dedup (novelty is a continuum; dedup is binary).
