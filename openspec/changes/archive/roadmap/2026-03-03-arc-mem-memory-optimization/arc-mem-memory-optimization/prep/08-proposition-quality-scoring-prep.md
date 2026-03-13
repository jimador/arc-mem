# Prep: Proposition Quality Scoring

## Feature Reference

Feature ID: `F08`. Change slug: `proposition-quality-scoring`. Wave 3. Priority: SHOULD.
Feature doc: `openspec/roadmaps/unit-memory-optimization/features/08-proposition-quality-scoring.md`
Research: `openspec/research/llm-optimization-external-research.md` (rec B)

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

1. **Two scoring axes**: Every incoming proposition MUST receive a novelty score [0.0, 1.0] and an importance score [0.0, 1.0]. No third axis (utility) in this iteration.
2. **Configurable thresholds**: Scoring thresholds MUST be configurable per `DomainProfile`. Different domains (narrative, factual) may have different quality bars.
3. **Observable scores**: Novelty and importance scores MUST appear in trust audit records or promotion outcome logs. Scores are not optional metadata -- they are first-class observable signals.
4. **Opt-in activation**: Quality scoring MUST be toggleable via configuration. Default is off until validated against existing simulation scenarios.
5. **No per-proposition LLM calls**: Scoring SHOULD NOT require individual LLM calls per proposition. Batch or heuristic approaches are strongly preferred.
6. **Complementary to dedup**: Quality scoring does not replace `DuplicateDetector`. Novelty is a continuum; dedup is binary. Both run independently.
7. **A/B testability**: All scoring implementations (heuristic, embedding-based, Prolog) MUST implement the same interface and MUST be selectable per-simulation. Scenario YAML MUST support specifying the scoring mechanism so the simulator can compare approaches on identical scenarios (e.g., heuristic-only baseline vs. heuristic+Prolog vs. embedding-based).

## Open Questions

These questions MUST be resolved during design/implementation.

### Q1: TrustSignal Integration vs. Pre-Trust Filter

**Question**: Should scoring be implemented as new `TrustSignal` implementations (Option A) or as a pre-trust filter that gates propositions before `TrustPipeline` (Option B)?

**Decision space**:
- **Option A (TrustSignal)**: Implement `NoveltySignal` and `ImportanceSignal` as `TrustSignal` implementations. Add them to the signal list in `TrustPipeline`. Configure weights in `DomainProfile`. Scores compose with existing signals (graphConsistency, sourceAuthority, extractionConfidence, corroboration) into the overall `TrustScore`.
  - Pro: Composable; leverages existing infrastructure; weights are already configurable per domain.
  - Con: Low-novelty propositions still run through the full pipeline (no early exit); `TrustScore` semantics shift (now includes value, not just reliability).
- **Option B (Pre-trust filter)**: Implement a `QualityFilter` that runs before `TrustPipeline` in the `UnitPromoter` pipeline. Propositions below quality threshold are rejected before trust evaluation.
  - Pro: Early rejection saves pipeline cost; clean separation between quality and trust.
  - Con: Scores are not in `TrustAuditRecord` unless explicitly injected; less composable; adds a new pipeline stage.

**Recommendation**: Option A. Composability and existing infrastructure outweigh the early-exit benefit. The `TrustPipeline` is already designed for pluggable signals, and `DomainProfile` already supports per-signal weights. If early exit proves necessary for performance, a weight of 0.0 for the quality signals effectively disables them without pipeline changes.

**Constraints**: Whichever option is chosen, scores MUST be observable in audit records. Option B, if chosen, MUST inject scores into `TrustAuditRecord` explicitly.

### Q2: Scoring Mechanism

**Question**: What mechanism computes novelty and importance scores?

**Decision space**:
- **(a) Heuristic**: Novelty via text similarity (Jaccard, cosine on TF-IDF) between proposition and existing context unit texts. Importance via entity overlap with recent conversation turns.
  - Pro: Zero LLM cost; fast; deterministic.
  - Con: Misses semantic novelty (different wording of same concept); importance heuristics are coarse.
- **(b) Embedding similarity**: Novelty via cosine distance between proposition embedding and nearest context unit embedding. Importance via cosine similarity to conversation topic embedding.
  - Pro: Semantic-aware; moderate cost (embedding calls are cheap relative to full LLM calls).
  - Con: Requires embedding model integration (not currently in context units); adds a dependency.
- **(c) Batched LLM call**: Score all candidate propositions in a single LLM call with the existing context unit set as context.
  - Pro: Most accurate; can capture subtle semantic relationships.
  - Con: LLM cost per promotion batch; latency; model-dependent accuracy.
- **(d) Hybrid**: Heuristic primary with LLM fallback for borderline scores (e.g., novelty between 0.3 and 0.7).
  - Pro: Low cost for clear cases; accuracy for ambiguous cases.
  - Con: Complexity; two code paths.
- **(e) Prolog relationship inference**: Derive logical relationships (entailment, support, subsumption) between propositions and existing context units via DICE's tuProlog projection. Project context units and candidate propositions as Prolog facts; express domain relationship rules as Prolog clauses; query via `PrologEngine.query()` / `queryAll()` / `findAll()`.
  - Pro: Deterministic; near-zero cost (in-process Prolog query); captures logical relationships that text similarity misses (e.g., "X is a dragon" + "X breathes fire" entails support via domain rules). Zero new dependencies — tuProlog (2p-kt) already on classpath via DICE 0.1.0-SNAPSHOT.
  - Con: Bounded by rule expressiveness; requires domain-specific Prolog rule authoring; does not capture purely semantic relationships (paraphrase, implication without explicit rules).

**Recommendation**: Start with (a) heuristic for initial implementation. The `DuplicateDetector` already computes text similarity; novelty scoring can extend that infrastructure. If heuristic accuracy proves insufficient in simulation validation, upgrade to (d) hybrid. Embedding-based (b) is a good follow-up but adds infrastructure scope. Prolog-based (e) SHOULD be evaluated as a complementary importance scorer — it excels at structural/logical relationship detection and can run alongside heuristic scoring at negligible additional cost.

**Constraints**: The chosen mechanism MUST process a batch of propositions (not one at a time). Per-proposition LLM calls MUST NOT be introduced.

### Q3: DomainProfile Weight Allocation

**Question**: What default weights should novelty and importance signals receive in the `DomainProfile` configuration?

**Decision space**:
- (a) Equal weight with existing signals (0.25 each if 4 existing + 2 new = 6 signals).
- (b) Lower weight (0.10 each) so quality scores are tie-breakers, not dominant.
- (c) Domain-dependent: narrative domains weight importance higher (story relevance matters); factual domains weight novelty higher (redundancy is the bigger problem).

**Recommendation**: Option (b) for the NARRATIVE default profile -- low weight so quality scoring acts as a refinement, not a gate-changer. Option (c) as a documented pattern for custom profiles. The goal is to filter obviously low-value propositions, not to reclassify borderline trust decisions.

**Constraints**: Default weights MUST NOT change existing promotion outcomes for high-quality propositions. If Option A (TrustSignal) is chosen, the sum of all signal weights MUST remain normalized in `TrustEvaluator`.

## Small-Model Task Constraints

Each implementation task MUST touch at most **4 files** (excluding test files). Each task MUST be independently verifiable via `./mvnw test`.

### Suggested Task Breakdown

1. **Task 1: Novelty scoring implementation** (4 files)
   - Implement novelty computation: compare proposition text against active context unit texts using text similarity.
   - If Option A: implement as `NoveltySignal` implementing `TrustSignal`.
   - If Option B: implement as standalone scorer callable from `QualityFilter`.
   - Files: `NoveltySignal.java` (or `NoveltyScorer.java`), `DomainProfile` (add weight config), `ArcMemProperties` (threshold config), test.

2. **Task 2: Importance scoring implementation** (4 files)
   - Implement importance computation: compare proposition against recent conversation context (entity overlap, topic relevance).
   - Match the integration pattern chosen in Task 1 (TrustSignal or standalone).
   - Files: `ImportanceSignal.java` (or `ImportanceScorer.java`), conversation context accessor, `DomainProfile` (add weight config), test.

3. **Task 3: Pipeline integration + filtering** (4 files)
   - Wire scoring into `UnitPromoter` pipeline (via TrustPipeline signal registration or pre-trust filter).
   - Implement threshold-based filtering for low-scoring propositions.
   - Add opt-in toggle to configuration.
   - Files: `TrustPipeline` or `UnitPromoter` (integration), `ArcMemProperties` (toggle + thresholds), Spring config bean, test.

4. **Task 4: Observability + audit trail** (3 files)
   - Ensure novelty and importance scores appear in `TrustAuditRecord` or promotion outcome logs.
   - Add quality filter rejection reason to promotion outcome logging.
   - Emit structured log events: rejection count at DEBUG, score distributions at TRACE.
   - Files: `TrustAuditRecord` (or promotion log), scoring logger, test.

5. **Task 5 (OPTIONAL): Prolog relationship scorer** (4 files)
   - Implement `PrologRelationshipScorer` that projects context units and candidate propositions as Prolog facts, expresses domain relationship rules as Prolog clauses, and queries for logical support/entailment/subsumption via DICE's `PrologEngine`.
   - Contributes to importance scoring: propositions that logically support or extend existing context units receive a higher importance score.
   - Match the integration pattern chosen in Tasks 1-3 (TrustSignal or standalone scorer).
   - Files: `PrologRelationshipScorer.java`, domain rule definitions (Prolog clause provider), `DomainProfile` (Prolog weight config), test.
   - **Prerequisite**: Familiarity with DICE Prolog projection API (`PrologEngine.query()`, `queryAll()`, `findAll()`). Currently zero Prolog usage in context units — this would be the first integration point.

## Gates

Implementation is complete when ALL of the following are satisfied:

1. **Scores computed**: Every proposition evaluated by the promotion pipeline MUST have novelty and importance scores in range [0.0, 1.0].
2. **Low-value filtering measurable**: Running simulation scenarios with quality scoring enabled MUST show measurable filtering of low-value propositions (rejection count > 0 for scenarios with redundant narrative elaboration).
3. **No regression on high-quality promotions**: Existing simulation scenarios MUST produce equivalent promotion outcomes for high-quality propositions with quality scoring enabled vs. disabled.
4. **Observability**: Scores MUST appear in trust audit records or promotion outcome logs. Filter rejection counts MUST be logged.

## Dependencies Map

```
(no feature dependencies)
                            ──► F08 (proposition-quality-scoring)

Integrates with (existing):
  - TrustPipeline / TrustSignal (for Option A)
  - UnitPromoter (pipeline integration)
  - DuplicateDetector (complementary, not replaced)
  - DomainProfile (weight configuration)

Optional integration (Task 5):
  - DICE PrologEngine (tuProlog / 2p-kt) — Prolog projection for relationship inference
  - Already on classpath via DICE 0.1.0-SNAPSHOT — zero new dependencies
```
