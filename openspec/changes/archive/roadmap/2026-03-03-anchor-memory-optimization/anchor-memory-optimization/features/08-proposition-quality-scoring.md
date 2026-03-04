# Feature: Proposition Quality Scoring

## Feature ID

`F08`

## Summary

Add novelty and importance scoring axes to proposition evaluation, inspired by sleeping-llm's curator (Amygdala) fact scoring. These axes complement the existing `TrustPipeline` signals to filter low-value propositions before they enter the promotion pipeline, improving budget efficiency and signal-to-noise ratio of the active anchor set.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: dice-anchors' `DuplicateDetector` is binary (duplicate or not), and `TrustPipeline` signals focus on reliability rather than value. A proposition can be non-duplicate, high-confidence, and well-sourced but still low-value -- a trivial elaboration of existing knowledge. As anchor budgets fill with organic propositions (via scene-setting and warm-up turns), distinguishing valuable from trivial propositions becomes critical for budget efficiency.
2. Value delivered: Research (sleeping-llm curator) shows that fact-level quality scoring (novelty, importance, utility) significantly improves the signal-to-noise ratio of injected knowledge. Adding novelty and importance axes prevents low-value propositions from consuming budget slots that could hold genuinely new or central information.
3. Why now: Wave 3 -- no hard dependencies. As anchor sets grow and mature through Waves 1-2 improvements, the volume of organic propositions increases, making quality-based filtering increasingly important.

## Scope

### In Scope

1. Define a novelty score [0.0, 1.0] per incoming proposition: how much new information does this proposition add relative to existing anchors?
2. Define an importance score [0.0, 1.0] per incoming proposition: how central is this proposition to the current conversation context?
3. Integrate scoring into the promotion pipeline -- low-scoring propositions (below configurable thresholds) SHOULD be filtered before promotion.
4. Make scores observable in trust audit records or promotion outcome logs.
5. Support configurable scoring thresholds per `DomainProfile`.
6. Evaluate two integration options: (A) new `TrustSignal` implementations added to the pipeline, or (B) pre-trust filter that gates propositions before `TrustPipeline` evaluation.
7. **Prolog relationship inference (scoring mechanism option)**: A `PrologRelationshipScorer` MAY derive proposition relationships (entailment, logical support, subsumption) via DICE's tuProlog projection. Prolog rules can detect logical support/contradiction patterns that pure embedding similarity misses — e.g., "X is a dragon" + "X breathes fire" entails support via domain rules, which text similarity would not capture. This contributes to importance scoring by identifying propositions that logically extend or support existing anchors. tuProlog (2p-kt) is already on classpath via DICE 0.1.0-SNAPSHOT; zero new dependencies REQUIRED.
8. **A/B testability**: All scoring mechanisms (heuristic, embedding-based, Prolog) MUST implement the same interface (`TrustSignal` or scorer interface). The simulator MUST support selecting scoring mechanisms per-scenario for direct comparison of scoring accuracy and promotion quality.

### Out of Scope

1. Utility scoring (sleeping-llm's third axis -- procedural knowledge detection). This is less relevant to dice-anchors' narrative domain.
2. Per-proposition LLM calls for scoring -- the design SHOULD favor batch or heuristic approaches.
3. Changes to existing `DuplicateDetector` -- novelty scoring is complementary, not a replacement.
4. Retroactive scoring of already-promoted anchors (proactive maintenance cycle F07 handles post-promotion quality assessment).

## Dependencies

1. Feature dependencies: None.
2. Technical prerequisites: `TrustPipeline`, `TrustSignal` interface, `DomainProfile`, `AnchorPromoter`, `DuplicateDetector`.
3. Priority: SHOULD.
4. OpenSpec change slug: `proposition-quality-scoring`.
5. Research rec: B.
6. Runtime dependency (Prolog scoring option): DICE 0.1.0-SNAPSHOT provides tuProlog (2p-kt) for Prolog projection. Already on classpath — zero new dependencies for the Prolog relationship scoring path.

## Research Requirements

1. Open questions:
   - Should scoring integrate as new `TrustSignal` implementations (Option A, composable) or as a pre-trust filter (Option B, early rejection)? Trade-off: composability vs. efficiency.
   - What scoring mechanism best balances accuracy and cost? Candidates: (a) heuristic-based (entity overlap, text similarity to existing anchors), (b) embedding similarity, (c) lightweight batched LLM call, (d) Prolog relationship inference via DICE tuProlog projection (deterministic logical relationship detection at near-zero cost).
   - What `DomainProfile` weight allocation for novelty and importance best serves narrative vs. factual domains?
   - What thresholds filter low-value propositions without rejecting genuinely important ones?
2. Required channels: `codebase`
3. Research completion gate: Scoring mechanism recommendation with accuracy/cost trade-off analysis SHOULD be completed before proposal.

## Impacted Areas

1. Packages/components: `anchor/` (new `TrustSignal` implementations or pre-trust filter), `extract/` (`AnchorPromoter` integration), `DiceAnchorsProperties` (scoring configuration, thresholds).
2. Data/persistence: No schema changes. Scores are computed per-proposition at promotion time and recorded in trust audit trail.
3. Domain-specific subsystem impacts: Promotion pipeline gains a quality gate. Simulation scenarios MAY observe different promotion rates depending on quality thresholds.
4. DICE Prolog integration (optional): A `PrologRelationshipScorer` MAY contribute to importance scoring by projecting propositions and anchors as Prolog facts and querying for logical relationships (entailment, support, subsumption) via `PrologEngine`. Lives in `anchor/` alongside other scoring components.

## Visibility Requirements

### UI Visibility

1. User-facing surface: RunInspectorView MAY display per-proposition novelty and importance scores alongside promotion outcomes.
2. What is shown: Scores for each evaluated proposition, filter rejection reason for low-value propositions.
3. Success signal: Promotion pipeline produces a higher-quality anchor set with fewer trivial propositions.

### Observability Visibility

1. Logs/events/metrics: Per-proposition novelty/importance scores. Filter rejection counts per turn. Score distributions per turn. Logger MUST emit rejection summary at DEBUG level.
2. Trace/audit payload: `TrustAuditRecord` SHOULD include novelty and importance scores. Promotion outcome logs SHOULD distinguish quality-filtered rejections from other rejection reasons (conflict, dedup, low trust).
3. How to verify: Log grep for `quality.filter.rejected` events; compare promotion rates with and without quality scoring enabled.

## Acceptance Criteria

1. Each incoming proposition MUST receive a novelty score in range [0.0, 1.0].
2. Each incoming proposition MUST receive an importance score in range [0.0, 1.0].
3. Low-scoring propositions (below configurable threshold) SHOULD be filtered before promotion.
4. Scoring SHOULD NOT require per-proposition LLM calls (batch or heuristic preferred).
5. Scores MUST be observable in trust audit records or promotion outcome logs.
6. Existing promotion outcomes for high-quality propositions MUST NOT change (no regression on currently-promoted propositions).
7. Scoring thresholds MUST be configurable per `DomainProfile`.
8. Quality scoring MUST be toggleable (opt-in via configuration; default off until validated).

## Risks and Mitigations

1. Risk: Over-aggressive quality filtering rejects genuinely important propositions.
   Mitigation: Conservative default thresholds; opt-in activation; no-regression validation against existing simulation scenarios.
2. Risk: Heuristic scoring is too coarse to capture semantic novelty.
   Mitigation: Start with heuristics; upgrade to embedding-based or LLM-batched scoring if heuristics prove insufficient. The `TrustSignal` integration (Option A) allows swapping implementations without pipeline changes.
3. Risk: Scoring adds latency to the promotion pipeline.
   Mitigation: Heuristic scoring is O(N * M) where N = candidates, M = active anchors (text comparison). This is negligible compared to existing LLM-based conflict detection.
4. Risk: Novelty scoring overlaps with existing `DuplicateDetector`.
   Mitigation: Novelty is a continuum [0.0, 1.0]; dedup is binary. Novelty captures "mostly redundant" propositions that pass binary dedup. They are complementary, not competing.

## Proposal Seed

### Suggested OpenSpec Change Slug

`proposition-quality-scoring`

### Proposal Starter Inputs

1. Problem statement: dice-anchors' duplicate detector is binary (duplicate or not), and TrustPipeline signals focus on reliability rather than value. A proposition can be non-duplicate, high-confidence, and well-sourced but still low-value (trivial elaboration of existing knowledge). Research (sleeping-llm curator) shows that fact-level quality scoring (novelty, importance, utility) significantly improves the signal-to-noise ratio of injected knowledge.
2. Why now: As anchor budgets fill with organic propositions (via scene-setting and warm-up turns), distinguishing valuable from trivial propositions becomes critical for budget efficiency.
3. Constraints: SHOULD be zero or low LLM cost. MUST NOT slow the extraction pipeline significantly. SHOULD be configurable per `DomainProfile`. MUST be toggleable.
4. Visible outcomes: Per-proposition scores in trust audit; filter rejection counts; improved promotion quality measurable in simulation.

### Suggested Capability Areas

1. Novelty scoring (information gain relative to existing anchor set).
2. Importance scoring (relevance to current conversation context).
3. TrustPipeline integration or pre-trust filter design.
4. DomainProfile-configurable thresholds and weights.
5. Prolog relationship inference (optional): logical relationship detection via DICE tuProlog projection as an importance scoring contributor.

### Candidate Requirement Blocks

1. Requirement: The promotion pipeline SHALL score incoming propositions on novelty [0.0, 1.0] and importance [0.0, 1.0] before trust evaluation.
2. Scenario: When a proposition "The tavern has wooden furniture" is evaluated against existing anchors including "The tavern is a cozy establishment with oak tables and chairs," the novelty score SHALL be low (< 0.3) and the proposition SHOULD be filtered.
3. Scenario: When a proposition "The dragon's name is Aldrathar" is evaluated with no existing anchors mentioning dragons, the novelty score SHALL be high (> 0.7) and the proposition SHALL proceed to trust evaluation.

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| Rec B | Sleeping-llm curator scores facts by novelty (message length, question marks, technical markers) and importance (correction markers, emphasis). Combined score = arithmetic mean; threshold filters low-quality exchanges. | `openspec/research/llm-optimization-external-research.md` sec 3.1 | High | Validates two-axis scoring design. dice-anchors adaptation uses semantic similarity rather than surface heuristics. |
| Curator | Two-tier extraction: 20+ regex patterns primary, model-based Q&A fallback. Dedup via (question, answer) tuple keys. | sleeping-llm `src/sleep/curator.py` | Medium | Heuristic-first approach is validated by sleeping-llm's design. |

## Validation Plan

1. Unit tests: Novelty scoring with known anchor sets and predictable proposition overlaps. Importance scoring with known conversation context. Threshold filtering with edge cases (scores at boundary).
2. Integration test: Full promotion pipeline with quality scoring enabled; verify low-value propositions filtered, high-value propositions unaffected.
3. Observability validation: Scores present in trust audit records; filter rejection counts logged.
4. Regression validation: Run existing simulation scenarios with quality scoring enabled; verify no change in promotion outcomes for high-quality propositions.

## Known Limitations

1. Heuristic novelty scoring (text similarity) may miss semantic novelty captured by different wording of the same concept.
2. Importance scoring depends on conversation context representation -- short conversations may not provide enough signal.
3. Sleeping-llm's third axis (utility -- procedural knowledge detection) is not included; it is less relevant to narrative domains but MAY be added later.
4. Quality scoring is computed at promotion time. Already-promoted anchors are not retroactively scored (use F07 proactive maintenance for post-promotion quality assessment).
5. Prolog relationship inference (if adopted) is bounded by the expressiveness of domain rules. Implicit relationships not captured by explicit Prolog clauses will be missed. Prolog scoring is best used as a complement to heuristic or embedding-based approaches, not a replacement.

## Suggested Command

`/opsx:new proposition-quality-scoring`
