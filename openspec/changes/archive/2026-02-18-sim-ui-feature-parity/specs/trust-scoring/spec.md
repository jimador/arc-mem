## ADDED Requirements

### Requirement: TrustScore record

A `TrustScore` record SHALL be added to the `anchor` package. The record SHALL contain: `score` (double, range [0.0, 1.0]), `authorityCeiling` (Authority enum value representing the maximum authority this score can warrant), `promotionZone` (PromotionZone enum), `signalAudit` (Map<String, Double> mapping signal names to their weighted contributions), and `evaluatedAt` (Instant timestamp). The `Anchor` record SHALL be extended with an optional `TrustScore` field (may be null for anchors created before trust scoring is active).

#### Scenario: TrustScore within valid range
- **WHEN** a TrustScore is created with score=0.85
- **THEN** the score value is 0.85 and is within [0.0, 1.0]

#### Scenario: Anchor record includes TrustScore
- **WHEN** an Anchor is constructed with a TrustScore
- **THEN** the Anchor's trustScore() accessor returns the TrustScore record

#### Scenario: Anchor without TrustScore
- **WHEN** an Anchor is constructed without a TrustScore (legacy or pre-trust)
- **THEN** the Anchor's trustScore() returns null

### Requirement: PromotionZone enum

A `PromotionZone` enum SHALL be defined with three values: `AUTO_PROMOTE` (proposition meets trust threshold for automatic anchor promotion), `REVIEW` (proposition requires human review before promotion), and `ARCHIVE` (proposition trust is too low, route to archive). Zone boundaries SHALL be determined by the active DomainProfile's threshold configuration.

#### Scenario: AUTO_PROMOTE zone
- **WHEN** a proposition's trust score is 0.82 and the profile's auto-promote threshold is 0.75
- **THEN** the promotion zone is AUTO_PROMOTE

#### Scenario: ARCHIVE zone
- **WHEN** a proposition's trust score is 0.15 and the profile's archive threshold is 0.30
- **THEN** the promotion zone is ARCHIVE

### Requirement: TrustSignal SPI and four implementations

A `TrustSignal` interface SHALL define the SPI with method `evaluate(PropositionNode, String contextId) -> OptionalDouble`. Four implementations SHALL be provided:

1. `SourceAuthoritySignal` — returns 0.9 for DM-sourced propositions, 0.3 for PLAYER-sourced, 1.0 for SYSTEM-sourced. Source determination SHALL use the proposition's sourceIds.
2. `ExtractionConfidenceSignal` — returns the DICE extraction confidence score directly from the PropositionNode.
3. `GraphConsistencySignal` — computes word-overlap similarity between the proposition text and existing active anchor texts in the same contextId via `AnchorRepository` queries. Returns 0.5 (neutral) when no anchors exist (cold-start).
4. `CorroborationSignal` — counts distinct sourceIds on the proposition. Single source returns 0.3, two sources return 0.6, three or more return 0.9.

#### Scenario: DM source authority signal
- **WHEN** `SourceAuthoritySignal.evaluate()` is called for a DM-sourced proposition
- **THEN** the signal returns 0.9

#### Scenario: Cold-start graph consistency
- **WHEN** `GraphConsistencySignal.evaluate()` is called with no existing anchors in the context
- **THEN** the signal returns 0.5

#### Scenario: Multi-source corroboration
- **WHEN** a proposition has 3 distinct sourceIds
- **THEN** `CorroborationSignal` returns 0.9

### Requirement: DomainProfile record and three profiles

A `DomainProfile` record SHALL define named weight configurations with fields: `name` (String), `weights` (Map<String, Double> mapping signal names to weights that sum to 1.0), `autoPromoteThreshold` (double), `reviewThreshold` (double), and `archiveThreshold` (double, below which propositions are archived). Three built-in profiles SHALL be provided:

1. `SECURE` — graph-heavy weights (graphConsistency=0.40, sourceAuthority=0.25, extractionConfidence=0.20, corroboration=0.15), strict thresholds (autoPromote=0.80, review=0.50, archive=0.30).
2. `NARRATIVE` — source-heavy weights (sourceAuthority=0.40, extractionConfidence=0.25, graphConsistency=0.20, corroboration=0.15), permissive thresholds (autoPromote=0.55, review=0.30, archive=0.15).
3. `BALANCED` — equal weights (0.25 each), moderate thresholds (autoPromote=0.70, review=0.40, archive=0.25).

#### Scenario: SECURE profile strict threshold
- **WHEN** the SECURE profile is active and a proposition scores 0.72
- **THEN** the promotion zone is REVIEW (below 0.80 autoPromote but above 0.50 review)

#### Scenario: NARRATIVE profile permissive threshold
- **WHEN** the NARRATIVE profile is active and a proposition scores 0.60
- **THEN** the promotion zone is AUTO_PROMOTE (above 0.55 autoPromote)

### Requirement: TrustEvaluator weighted sum with zone routing

`TrustEvaluator` SHALL compute the composite trust score as a weighted sum of all available TrustSignal evaluations. When a signal returns `OptionalDouble.empty()` (absent), its weight SHALL be redistributed proportionally among the remaining signals. After computing the score, the evaluator SHALL determine the PromotionZone by comparing the score against the active DomainProfile's thresholds. The authority ceiling SHALL be derived from the score: score >= 0.80 maps to RELIABLE ceiling, 0.50-0.79 maps to UNRELIABLE ceiling, below 0.50 maps to PROVISIONAL ceiling. CANON SHALL never be the authority ceiling (per Invariant A3).

#### Scenario: All signals present
- **WHEN** all four signals return values and the BALANCED profile is active
- **THEN** the trust score equals the weighted sum with each signal contributing 0.25

#### Scenario: Missing signal weight redistribution
- **WHEN** GraphConsistencySignal returns empty and BALANCED profile is active
- **THEN** the remaining three signals each receive ~0.333 weight (redistributed from 0.25 each)

#### Scenario: Authority ceiling from score
- **WHEN** the computed trust score is 0.85
- **THEN** the authority ceiling is RELIABLE

#### Scenario: CANON never assigned as ceiling
- **WHEN** the trust score is 1.0 (maximum possible)
- **THEN** the authority ceiling is still RELIABLE, not CANON

### Requirement: TrustPipeline facade

`TrustPipeline` SHALL be a facade composing the `TrustEvaluator` and zone routing logic. It SHALL provide a single method `evaluate(PropositionNode, String contextId) -> TrustScore` that collects all signals, delegates to TrustEvaluator for scoring, and returns the complete TrustScore. The pipeline SHALL be injected into `AnchorPromoter`.

#### Scenario: Pipeline produces complete TrustScore
- **WHEN** `TrustPipeline.evaluate()` is called for a proposition
- **THEN** the returned TrustScore contains score, authorityCeiling, promotionZone, signalAudit, and evaluatedAt

### Requirement: Integration with AnchorPromoter

`AnchorPromoter` SHALL use the `TrustPipeline` to score propositions before making promotion decisions. The current confidence-threshold promotion SHALL be replaced with trust-scored promotion. Propositions in the AUTO_PROMOTE zone SHALL be promoted automatically. Propositions in the REVIEW zone SHALL be flagged for review (queued, not auto-promoted). Propositions in the ARCHIVE zone SHALL not be promoted.

#### Scenario: Auto-promote via trust pipeline
- **WHEN** `AnchorPromoter` processes a proposition and TrustPipeline returns zone=AUTO_PROMOTE
- **THEN** the proposition is promoted to anchor via `AnchorEngine.promote()`

#### Scenario: Review zone proposition not auto-promoted
- **WHEN** TrustPipeline returns zone=REVIEW for a proposition
- **THEN** the proposition is NOT automatically promoted
- **AND** it is flagged for human review

### Requirement: Trust score display in ContextInspectorPanel

The ContextInspectorPanel anchor cards SHALL display the trust score when available. Each anchor card SHALL show the composite score as a percentage, the promotion zone badge, and an expandable section showing individual signal contributions from the signalAudit map. The authority ceiling SHALL be displayed alongside the actual authority.

#### Scenario: Trust score shown on anchor card
- **WHEN** an anchor has a TrustScore with score=0.78
- **THEN** the anchor card displays "Trust: 78%" and the promotion zone badge

#### Scenario: Signal audit expandable
- **WHEN** the user expands the trust details on an anchor card
- **THEN** individual signal contributions are shown (e.g., "sourceAuthority: 0.90, graphConsistency: 0.65, ...")

### Requirement: Trust profile configuration in scenario YAML

`SimulationScenario` SHALL support a `trustEvaluation` section in the YAML with fields: `profile` (String, one of SECURE/NARRATIVE/BALANCED), and optional `weightOverrides` (Map<String, Double> to override individual signal weights). The `ScenarioLoader` SHALL parse this section and pass it to the TrustPipeline.

#### Scenario: Scenario with SECURE profile
- **WHEN** a scenario YAML contains `trustEvaluation: { profile: SECURE }`
- **THEN** the simulation uses the SECURE domain profile for trust evaluation

#### Scenario: Scenario with weight overrides
- **WHEN** a scenario YAML contains weight overrides `{ sourceAuthority: 0.50, graphConsistency: 0.50 }`
- **THEN** those weights replace the profile defaults (and are renormalized to sum to 1.0)

### Requirement: Trust-based assertions

Simulation scenarios SHALL support trust-based assertions: `trust-score-range` (verify all anchor trust scores fall within a specified [min, max] range), `no-canon-auto-assigned` (verify CANON authority was never set automatically), `authority-at-most` (verify no anchor exceeds a specified authority level), and `promotion-zone` (verify expected promotion zone distribution). These assertions integrate with the assertion-framework capability.

#### Scenario: trust-score-range assertion passes
- **WHEN** a scenario asserts `trust-score-range: { min: 0.40, max: 0.95 }`
- **AND** all anchor trust scores are within [0.40, 0.95]
- **THEN** the assertion passes

#### Scenario: no-canon-auto-assigned assertion fails
- **WHEN** a scenario asserts `no-canon-auto-assigned`
- **AND** an anchor was automatically assigned CANON authority
- **THEN** the assertion fails with details identifying the offending anchor
