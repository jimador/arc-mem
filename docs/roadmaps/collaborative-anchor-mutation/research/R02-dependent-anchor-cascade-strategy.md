# Research Task: Dependent Anchor Cascade Strategy

## Task ID

`R02`

## Question

Which cascade strategy (temporal co-creation, semantic dependency, explicit DERIVED_FROM edges, subject clustering) best identifies dangling propositions without over-invalidation?

## Why This Matters

1. Decision blocked by uncertainty: F03 (dependent-anchor-cascade) requires a strategy for identifying which anchors are logically dependent on a superseded anchor. No strategy has been evaluated empirically.
2. Potential impact if wrong: Over-cascade removes independent anchors, destroying established facts unnecessarily. Under-cascade leaves inconsistent orphaned anchors that confuse the DM.
3. Related feature IDs: F03, F04.

## Scope

### In Scope

1. Evaluate at least 4 cascade strategies:
   - **Temporal co-creation**: Anchors extracted in the same turn as the superseded anchor are dependency candidates.
   - **Semantic dependency**: LLM-based evaluation of whether anchor B is logically dependent on anchor A.
   - **Explicit graph edges**: A `DERIVED_FROM` relationship in Neo4j between propositions (requires extraction-time lineage tracking).
   - **Subject clustering**: Anchors sharing the same subject entity (e.g., "Anakin Skywalker") that were created in the same extraction batch.
2. Define metrics: precision (no over-invalidation), recall (no orphans), latency, complexity.
3. Recommend a primary strategy with fallback.

### Out of Scope

1. Implementation of the chosen strategy (deferred to F03 OpenSpec change).
2. Multi-hop transitive cascades beyond configurable depth.
3. Cross-domain evaluation (D&D test scenarios only).

## Research Criteria

1. Required channels: `codebase`, `web`, `repo-docs`
2. Source priority order: codebase (existing extraction pipeline, supersession flow) > web (belief revision cascades, knowledge graph maintenance) > repo-docs
3. Freshness window: 24 months (2024-2026)
4. Minimum evidence count: 3
5. Timebox: 8h
6. Target confidence: `medium`

## Method

1. Local code/doc checks: Review extraction batch metadata in `AnchorPromoter`; check if temporal co-creation data is available; review `PropositionNode` graph relationships.
2. External evidence: Search for cascade invalidation strategies in knowledge graph maintenance literature; belief revision network propagation.
3. Prototype validation: Construct test cases from the R00 Playwright test data (wizard→bard) and evaluate each strategy's precision/recall.

## Findings

| Evidence ID | Channel | Source | Date Captured | Key Evidence | Reliability |
|-------------|---------|--------|---------------|--------------|-------------|
| E01 | codebase | `PropositionNode.java` — `created` (Instant), `contextId` (String) fields | 2026-02-25 | Every `PropositionNode` stores a `created` timestamp (set at `Instant.now()` in the constructor). All propositions in the same extraction batch share the same `contextId`. The `created` field provides a temporal co-creation signal: propositions created within the same extraction window (same turn) will have very close timestamps. No explicit per-turn batch ID is stored on the node; the correlation must be inferred from timestamp proximity or from the `grounding` field (chunk IDs that produced the proposition). | high |
| E02 | codebase | `PropositionNode.java` — `sourceIds` (List<String>), `grounding` (List<String>) fields | 2026-02-25 | `sourceIds` carries lineage tags (e.g., `"dm"`, `"player"`, `"system"`) appended by `AnchorRepository.tagPropositionsWithSource()`. `grounding` carries chunk IDs. Neither field encodes a per-turn batch identifier. Co-created propositions can only be identified by timestamp proximity or by their shared `grounding` chunk IDs — they do not share a batch ID. This means temporal co-creation heuristic must use timestamp proximity rather than an explicit batch key. | high |
| E03 | codebase | `AnchorRepository.java` — `createSupersessionLink()`, `findSupersessionChain()` | 2026-02-25 | The only inter-proposition relationship currently in the Neo4j graph is `SUPERSEDES` (successor → predecessor). It is created by `createSupersessionLink()` and traversed by `findSupersessionChain()` (bounded to depth 50). No `DERIVED_FROM`, `CO_CREATED_WITH`, or `HAS_SUBJECT` relationships exist. Adding `DERIVED_FROM` requires both schema extension and extraction-time population logic in `AnchorPromoter` or the DICE extraction pipeline. | high |
| E04 | codebase | `AnchorEngine.java` — `supersede()` method (lines 686–713) | 2026-02-25 | `AnchorEngine.supersede(predecessorId, successorId, reason)` archives the predecessor, creates the `SUPERSEDES` link, and publishes a `Superseded` event. It has no cascade logic. The method is the natural insertion point for F03: after creating the supersession link, a cascade evaluation hook could run against active anchors in the same `contextId`. The `contextId` is available from the predecessor node lookup already performed in the method. | high |
| E05 | codebase | `AnchorPromoter.java` — `batchEvaluateAndPromoteWithOutcome()` | 2026-02-25 | Batch promotion processes propositions from a single DICE extraction call together through the confidence → dedup → conflict → trust → promote pipeline. All propositions in the batch share the same `contextId` and are extracted from the same turn's DM/player text. The batch is the natural unit of temporal co-creation: propositions extracted in the same `batchEvaluateAndPromote` call were produced from the same turn. However, the batch is transient — no batch ID or turn timestamp is persisted to Neo4j on the proposition nodes during promotion. | high |
| E06 | codebase | `F03` feature doc — Acceptance Criteria | 2026-02-25 | F03 specifies: PROVISIONAL and UNRELIABLE anchors SHOULD be cascade-invalidated; RELIABLE anchors SHOULD be flagged for review; CANON anchors MUST NOT be cascade-invalidated. This authority-tiered cascade gate aligns with the AGM minimal change principle (E10) and ECO interchangeability evaluation (E12): higher-authority anchors require stronger evidence before invalidation. | high |
| E07 | codebase | `AnchorRepository.java` — `findActiveAnchors(contextId)` | 2026-02-25 | The repository can efficiently fetch all active anchors for a context. A cascade evaluation post-supersession would call `findActiveAnchors(contextId)` to get the candidate set, then apply the chosen dependency detection strategy against each candidate. For a 20-anchor budget the candidate set is small enough for LLM batch evaluation. | high |
| E08 | literature | Doyle, J. "A Truth Maintenance System." *Artificial Intelligence* 12(3), 1979 (cited in R05-E01) | 2026-02-25 | JTMS label propagation: each belief has a justification record (IN-list: supporting beliefs; OUT-list: beliefs that must be absent for this belief to hold). When a support belief is retracted, it is marked OUT. All beliefs whose justification sets become empty (no remaining IN-support) are also marked OUT recursively. The dependency graph is explicit and pre-built at assertion time. This is the gold standard for cascade correctness but requires explicit justification wiring at assertion time — equivalent to requiring `DERIVED_FROM` edges to be created when propositions are first promoted. | high (foundational paper) |
| E09 | literature | de Kleer, J. "An Assumption-based TMS." *Artificial Intelligence* 28(2), 1986 (cited in R05-E02) | 2026-02-25 | ATMS pre-computes all consistent environments. Retraction eliminates all environments containing the retracted assumption — no cascading traversal needed at retraction time because the dependency structure is fully pre-computed. Superior to JTMS for interactive systems (no backtracking), but the upfront computation cost and storage are much higher. For the anchor system, the proposition count is small (≤20 budget), making pre-computation feasible but over-engineered. | high (foundational paper) |
| E10 | literature | Alchourrón, C., Gärdenfors, P., Makinson, D. (1985) — AGM belief revision (cited in R05-E03, R05-E04) | 2026-02-25 | AGM minimal change principle: revision should preserve as much of the original belief set as possible. Only retract what is strictly necessary to accommodate the new belief. Applied to anchor cascade: when superseding anchor A, only invalidate anchors whose truth depends specifically on A's content — not all anchors sharing A's subject or time of creation. This argues against broad heuristics (temporal co-creation, subject clustering) as primary strategies because they retract too many independent beliefs. Semantic dependency or explicit edges are more consistent with minimal change. | high (foundational paper) |
| E11 | literature | Institute of Configuration Management, CMII Standard; ISO 10007:2017 (cited in R05-E08) | 2026-02-25 | ECO interchangeability analysis: before cascading a change, evaluate whether the dependent item is still "interchangeable" (functionally valid) despite the parent change. Class I changes (form/fit/function impacted) require full cascade; Class II changes (documentation only) have no cascade requirement. Applied to anchor cascade: an "interchangeability check" would ask "does anchor B remain true if anchor A changes?" — which is exactly semantic dependency evaluation. The ECO pattern supports a two-phase cascade: (1) identify candidate dependents, (2) evaluate each for interchangeability, invalidate only non-interchangeable ones. | high (industry standard) |
| E12 | web | Doyle 1979 / standard TMS literature — soft cascade / flag-for-review pattern (R05 context, pattern P11) | 2026-02-25 | Soft cascade: rather than auto-invalidating all dependent beliefs, flag them for human review. The system marks dependents as "unsupported" or "questionable" without immediately removing them. Clinicians/operators then decide which to retain. This is the medical records amendment cascade model (HIPAA: if a PHI record was disclosed, recipients must be notified — but the amendment does not automatically retract clinical decisions based on the original). | high (well-established pattern) |
| E13 | web | AGM literature — depth limiting for practical belief revision implementations | 2026-02-25 | Configurable cascade depth prevents runaway invalidation in large belief networks. Depth 1 (direct dependents of the superseded anchor only) is the standard default. Depth 2+ requires explicit configuration. For the anchor system, depth-1 cascade handles the primary use case (wizard→bard: class-specific spells are 1 hop from the class anchor). Depth-2 would cover spells whose associated ability scores are themselves dependent on the class — a rarer and more speculative dependency. | medium (inferred from TMS practice) |
| E14 | repo-docs | R04-E04, R04-E06 — Graphiti temporal supersession and edge invalidation | 2026-02-25 | Graphiti uses recency-based supersession: newest assertion always wins. No cascade logic exists for dependent facts — Graphiti treats each fact as independently managed. When a fact changes, dependent facts are not evaluated. This confirms the pattern observed across all surveyed AI frameworks: no framework has implemented dependent-fact cascade. The anchor system would be the first to do so explicitly. | high (primary paper + source code review) |
| E15 | web | Knowledge graph maintenance literature — subject clustering as a candidate dependency signal | 2026-02-25 | Subject clustering: anchors with the same subject entity (identified by NER or entity mention normalization) are treated as dependency candidates when the subject's primary characterization changes. Evidence: in knowledge graph completion literature, entity-centric fact grouping is used for consistency enforcement (e.g., ensuring all facts about "Paris" agree on its country). However, subject clustering is a precision-weak signal: "Anakin is human" shares the subject "Anakin" with "Anakin is a wizard" but is not logically dependent on the class anchor. | medium (survey of KG completion literature) |
| E16 | web | Wikipedia / Wikidata knowledge graph — predicate-based dependency modeling | 2026-02-25 | In Wikidata, property constraints (e.g., `P279 subclass-of` constraints) define which property values depend on other property values. A class change triggers constraint violation flags on dependent properties. This is a predicate-typed dependency model: dependency is declared at the property-type level, not the instance level. For anchors, a proposition typed as "class ability" would depend on a proposition typed as "character class." This requires structured predicate tagging during extraction — which DICE does not currently provide. | medium (Wikidata documentation) |

## Analysis

### Strategy Comparison Matrix

| Strategy | Precision (no over-invalidation) | Recall (no orphans) | Latency | Complexity | Neo4j Fit | Implementation Cost |
|----------|----------------------------------|---------------------|---------|------------|-----------|---------------------|
| Temporal co-creation | Low–Medium | Medium | Very low (timestamp query) | Low | Good (query by `created` window + `contextId`) | Low — no schema change, no new metadata |
| Subject clustering | Low | Medium–High | Low (NER + filter) | Medium | Medium (requires entity extraction at assertion time) | Medium — entity field needed on PropositionNode |
| Semantic dependency (LLM) | High | High | High (LLM call per candidate) | Medium | Good (candidates from `findActiveAnchors`) | Medium — LLM cost per cascade event |
| Explicit DERIVED_FROM edges | Very high | High | Very low (graph traversal) | High | Excellent (native Neo4j relationship traversal) | High — schema change + extraction-time edge creation |

**Precision analysis**: Temporal co-creation conflates co-occurrence with dependency. In the wizard→bard scenario, the extraction turn that produced "Anakin is a wizard" may also have produced "Anakin is human" and "Anakin has a Sage background." All three would be cascade candidates under temporal co-creation, but only the wizard-specific anchors are actually dependent. This produces false positives proportional to how many independent facts were co-extracted with the superseded anchor.

**Recall analysis**: Semantic dependency and DERIVED_FROM edges have the highest recall because they identify logical dependency regardless of when the anchor was created. A wizard-class anchor extracted on turn 1 can be identified as dependent on a class anchor extracted on turn 3 if the LLM or the graph edge encodes that relationship. Temporal co-creation misses cross-turn dependencies entirely.

**Neo4j fit**: The existing `SUPERSEDES` relationship pattern (directional, property-bearing, bounded traversal up to depth 50) is directly applicable to `DERIVED_FROM`. The `createSupersessionLink()` + `findSupersessionChain()` pattern is a proven template for adding `DERIVED_FROM` with the same schema and query conventions.

### JTMS Mapping to the Existing `SUPERSEDES` Model

JTMS requires that justification records are built at belief-assertion time. In anchor terms, this means `DERIVED_FROM` edges must be created when a proposition is promoted, not at cascade time. The existing `SUPERSEDES` relationship is created at supersession time — a different point in the lifecycle. `DERIVED_FROM` would need to be created at promotion time, which requires the extraction pipeline (`AnchorPromoter.batchEvaluateAndPromoteWithOutcome`) to infer and record dependencies among the batch being promoted.

This is feasible: within a single extraction batch, the LLM prompt used to extract propositions could also be extended to classify each proposition as "derived from" another proposition in the batch. However, cross-batch dependencies (anchor B created on turn 5 depends on anchor A created on turn 1) would require a post-hoc LLM dependency evaluation — the same cost as on-demand semantic dependency evaluation.

**Practical conclusion**: Full JTMS requires `DERIVED_FROM` edges; a partial JTMS can be approximated by on-demand semantic dependency evaluation at cascade time, treating the set of active anchors as the justification graph and the LLM as the entailment oracle.

### Cascade Depth Recommendation

Depth 1 (direct dependents of the superseded anchor) covers the primary use case: wizard→bard invalidates class-specific abilities and spells. Depth 2 (dependents of dependents) would catch ability score anchors that depend on class abilities — a valid but rarer case. Depth 1 as the configurable default with depth 2 available is the recommendation, consistent with F03 acceptance criteria.

### Hard vs. Soft Cascade

The authority-tiered approach in F03 acceptance criteria already implements a hybrid:
- PROVISIONAL and UNRELIABLE: hard cascade (auto-archive).
- RELIABLE: soft cascade (flag for review, do not auto-archive).
- CANON: exempt.

This is consistent with the ECO Class I/Class II pattern (E11) and the medical records amendment model (E12): minor/tentative facts are auto-corrected; established facts require human confirmation.

### Can Strategies Be Combined?

Yes. A two-phase cascade is the recommended architecture:

**Phase 1 — Fast filter (temporal co-creation + subject clustering as heuristics)**
Run immediately after supersession. Takes candidates from `findActiveAnchors(contextId)` filtered by same-turn timestamp proximity and/or matching subject entity. This is zero-LLM-cost and produces the candidate set for phase 2. Expected to have high recall (catches most true dependents) but low precision (includes false positives).

**Phase 2 — Precision filter (semantic dependency LLM evaluation)**
For each candidate from phase 1, ask the LLM: "Given that [superseded anchor text] has been revised to [successor anchor text], does [candidate anchor text] remain true, become false, or is it independent?" Only candidates classified as "becomes false" proceed to invalidation. Candidates classified as "independent" are spared.

This two-phase architecture:
- Is consistent with the ECO interchangeability evaluation pattern (E11).
- Applies the AGM minimal change principle (E10): only retract what semantic dependency confirms is dependent.
- Reserves LLM cost for candidates that pass the fast filter (reduces LLM calls significantly when most active anchors are unrelated to the superseded anchor's subject).
- Does not require schema changes for the initial implementation.

If `DERIVED_FROM` edges are added in a future iteration (F04 extension), phase 1 can be replaced by a graph traversal, reducing the LLM call count further and improving precision to near-perfect.

## Recommendation

**Primary strategy: Two-phase cascade (temporal co-creation as fast filter + semantic dependency as precision filter)**

**Rationale**:
1. Avoids schema changes for initial F03 implementation — no `DERIVED_FROM` relationship is needed. The strategy uses existing `PropositionNode.created` timestamps and the existing `findActiveAnchors(contextId)` query.
2. Precision is controlled by phase 2 LLM evaluation — independent anchors (e.g., "Anakin is human") are correctly spared because the LLM identifies them as independent of the class change.
3. LLM cost is bounded by the phase 1 filter: for a 20-anchor budget, phase 1 might yield 3–6 candidates sharing the superseded anchor's extraction turn; only those 3–6 incur LLM evaluation, not all 20.
4. Consistent with JTMS label propagation (E08) as an approximation: the LLM plays the role of the justification oracle; candidates play the role of IN-beliefs whose justification may be voided.
5. Consistent with AGM minimal change (E10): only anchors confirmed dependent by the LLM are retracted.
6. Authority-tiered hard/soft cascade is specified in F03 and aligns with ECO Class I/II pattern (E11): auto-invalidate PROVISIONAL/UNRELIABLE, flag RELIABLE, exempt CANON.

**Fallback if semantic LLM evaluation is unavailable (degraded mode)**:
Fall back to temporal co-creation only, but demote (not archive) candidates rather than invalidating them. This is conservative: it reduces the rank and authority of potentially-dependent anchors without destroying them, allowing subsequent reinforcement to restore them if they prove still valid.

**`DERIVED_FROM` edges deferred to a future iteration**: Add as a follow-on to F03 (or as an F04 extension) once the extraction pipeline is extended to track intra-batch derivation relationships. This would upgrade phase 1 from a timestamp heuristic to a precise graph traversal, approaching full JTMS correctness.

**Configuration parameters for F03 implementation**:
- `cascade.depth`: default 1, configurable.
- `cascade.temporalWindowMs`: time window for co-creation heuristic (default: 2000ms — tight enough to group a single extraction batch).
- `cascade.autoInvalidateAuthorities`: default `[PROVISIONAL, UNRELIABLE]`.
- `cascade.reviewAuthorities`: default `[RELIABLE]`.
- `cascade.strategy`: enum `TEMPORAL_ONLY | TEMPORAL_PLUS_SEMANTIC | SEMANTIC_ONLY`, default `TEMPORAL_PLUS_SEMANTIC`.

## Impact

1. Roadmap changes: Strategy choice may affect F04 scope (provenance metadata needed for temporal co-creation strategy).
2. Feature doc changes: F03 acceptance criteria will be updated with the chosen strategy.
3. Proposal scope changes: If LLM-based semantic dependency is chosen, F03 gains an LLM cost dependency.

## Remaining Gaps

1. **Extraction-time turn tagging not persisted**: The `AnchorPromoter` processes propositions in batches corresponding to a single turn, but no per-turn batch identifier is written to `PropositionNode`. The temporal co-creation heuristic must use timestamp proximity (`created` field within a narrow window) rather than an explicit batch key. If the window is too narrow, slow LLM extraction spread over multiple seconds may split a logically-cohesive batch into multiple groups. **Mitigation**: Add a `extractionBatchId` or `turnId` field to `PropositionNode` and populate it in `AnchorPromoter`. Low complexity, high impact on cascade quality.
2. **No empirical precision/recall measurement**: The strategy comparison matrix ratings (Low/Medium/High) are based on reasoning and analogy, not measured on the D&D wizard→bard scenario. R02 was scoped to D&D test scenarios but no ground-truth dependency labels exist to compute actual precision/recall. Before committing to implementation, creating a small labeled test set (10–20 wizard/bard anchor pairs with known dependency relationships) and evaluating the LLM semantic dependency classifier against them would increase confidence from `medium` to `high`.
3. **No evaluation of LLM semantic dependency classifier prompt**: The LLM prompt design for phase 2 (the "interchangeability" question) has not been prototyped. The prompt framing ("does [candidate] remain true if [superseded anchor] is revised to [successor anchor]?") needs empirical validation. False negative rate (independent anchors classified as dependent) is the critical failure mode.
4. **Cross-turn dependencies not addressed**: The temporal co-creation phase 1 filter only identifies same-turn co-created anchors. Anchors extracted on a later turn that are logically derived from an earlier anchor (e.g., a spell anchor extracted on turn 5 that depends on a class anchor from turn 1) will not be caught by phase 1. Phase 2 can catch them only if they happen to be in the candidate set. Without `DERIVED_FROM` edges, cross-turn logical dependencies can only be caught by running semantic dependency evaluation against all active anchors — not just the temporal co-creation subset. This represents a recall gap. Mitigated for the common case (most dependencies are intra-turn) but not eliminated.
