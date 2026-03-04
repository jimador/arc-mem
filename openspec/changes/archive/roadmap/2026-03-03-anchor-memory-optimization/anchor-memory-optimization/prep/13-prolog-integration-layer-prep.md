# Prep: Prolog Integration Layer

## Feature Reference

Feature ID: `F13`. Change slug: `prolog-integration-layer`. Wave 4. Priority: SHOULD.
Feature doc: `openspec/roadmaps/anchor-memory-optimization/features/13-prolog-integration-layer.md`
Research: `openspec/roadmaps/anchor-memory-optimization-roadmap.md` (cross-cutting Prolog strategy table), `openspec/research/llm-optimization-external-research.md` (rec A)

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

1. **tuProlog (2p-kt) via DICE 0.1.0-SNAPSHOT**: All Prolog functionality MUST use the `PrologEngine` already on the classpath. Zero new dependencies. No alternative Prolog engines, no Drools, no Evrete.
2. **A/B testable**: Every Prolog implementation MUST be paired with its existing non-Prolog counterpart behind the same interface. The simulator MUST support selecting between them per-scenario via YAML config.
3. **Proposition-to-Prolog-fact projection as shared foundation**: `AnchorPrologProjector` is the single entry point for all anchor-to-Prolog conversions. All three Prolog implementations consume this projector -- no independent projection logic.
4. **Interface compliance**: Prolog implementations MUST implement existing interfaces. `PrologConflictDetector` plugs into `CompositeConflictDetector` via the `LOGICAL` strategy branch. `PrologInvariantEnforcer` implements `ComplianceEnforcer`. `PrologAuditPreFilter` integrates with `ProactiveMaintenanceStrategy` via the existing pre-filter toggle.
5. **Sub-second query latency**: All Prolog queries MUST complete in < 100ms for anchor sets up to budget max (default 20). This is a hard performance gate, not a guideline.
6. **Prolog is a pre-filter, not a replacement**: Prolog handles logically decidable questions deterministically. Semantic contradictions that are not representable as Prolog facts still require LLM evaluation. No implementation MAY remove the LLM fallback path.
7. **Three implementations, prioritized**: (1) LOGICAL conflict detection, (2) Prolog audit pre-filter, (3) Prolog invariant enforcer. If scope must be cut, cut from the bottom.
8. **Rules extensible per DomainProfile**: Prolog rule sets SHOULD be extensible per `DomainProfile`. Default rules are domain-independent (negation patterns, incompatible states). Domain-specific rules are additive.

## Open Questions

These questions MUST be resolved during design/implementation. Each question includes the decision space and recommended approach.

### Q1: Prolog Fact Schema for Anchors

**Question**: What Prolog fact schema best represents anchors for contradiction detection, invariant checking, and audit pre-filtering?

**Decision space**:
- (a) Flat fact per anchor: `anchor(Id, Text, Authority, Rank, Pinned)`. Simple but text is opaque to Prolog reasoning.
- (b) Entity-triple decomposition: `anchor(Id, Authority, Rank, Pinned)` + `entity(Id, Subject, Predicate, Object)` extracted from anchor text. Enables relational reasoning but requires text decomposition.
- (c) Hybrid: flat anchor facts for metadata queries + entity triples for semantic queries. Separate fact families for different query types.
- (d) Token-level: `anchor_token(Id, Token, Position)`. Maximum granularity but high fact count and limited reasoning value.

**Recommendation**: Option (c) -- hybrid schema. Flat `anchor/5` facts carry metadata (authority, rank, pinned). Entity triples `claim(Id, Subject, Predicate, Object)` carry semantics. Contradiction rules operate on `claim/4`; invariant rules operate on `anchor/5`. This separates concerns cleanly.

**Constraints**: Text decomposition into entity triples SHOULD use simple heuristic extraction (subject-verb-object patterns), not LLM calls. The projection itself MUST NOT require LLM calls -- that would defeat the purpose of deterministic pre-filtering. Entity extraction quality is a known limitation; Prolog coverage depends on it.

### Q2: Contradiction Detection Rules

**Question**: What Prolog rule patterns are most effective for detecting contradictions among projected anchor facts?

**Decision space**:
- (a) Negation-based: `contradicts(A, B) :- claim(A, S, P, O), claim(B, S, P, O2), negates(O, O2)`. Detects "X is alive" vs. "X is dead" via a negation lookup table.
- (b) Incompatible states: `contradicts(A, B) :- claim(A, S, P, O), claim(B, S, P, O2), incompatible(O, O2)`. Broader than negation -- "X is in Paris" vs. "X is in London" via incompatibility rules.
- (c) Temporal inconsistency: `contradicts(A, B) :- claim(A, S, P, O), claim(B, S, P2, O2), temporal_conflict(P, O, P2, O2)`. Detects "X was born in 1980" vs. "X is 30 years old in 2025".
- (d) Layered: negation first (cheapest), then incompatible states, then temporal. Short-circuit on first match.

**Recommendation**: Option (d) -- layered with short-circuit. Negation is the highest-value, lowest-cost check. Incompatible states are a natural extension. Temporal consistency is the most complex and MAY be deferred. Each layer SHOULD have an independent enable/disable flag.

**Constraints**: Negation lookup tables and incompatibility rules MUST be provided as Prolog facts (not hardcoded in Java). Default tables SHOULD cover common patterns (alive/dead, present/absent, true/false, open/closed). Domain-specific tables are extensible per `DomainProfile`.

### Q3: Complex Text Projection

**Question**: How to handle Prolog projection for anchors with complex, multi-clause text?

**Decision space**:
- (a) First-sentence heuristic: extract entity triple from the first clause only. Fast, lossy.
- (b) Multi-claim extraction: decompose multi-clause text into multiple `claim/4` facts per anchor. More complete but higher fact count.
- (c) Keyword extraction: extract subject and key state words without full triple decomposition. Less structured but tolerant of irregular text.
- (d) LLM-assisted extraction (cached): use an LLM call at projection time to decompose text into triples, cache results. High quality but adds LLM cost at projection time.

**Recommendation**: Option (b) -- multi-claim extraction via heuristic decomposition. Split on sentence boundaries, extract one `claim/4` per sentence. This provides reasonable coverage without LLM costs. Option (c) as fallback for sentences that do not parse into clean triples.

**Constraints**: Projection MUST NOT use LLM calls (option d is rejected). Heuristic decomposition need not be perfect -- Prolog is a pre-filter, and false negatives (missed contradictions) are acceptable because LLM evaluation catches them downstream.

## Small-Model Task Constraints

Each implementation task MUST touch at most **5 files** (excluding test files). Each task MUST be independently verifiable via `./mvnw test`.

### Suggested Task Breakdown

1. **Task 1: AnchorPrologProjector foundation** (3 files)
   - Create `AnchorPrologProjector` in `anchor/` package.
   - Implement hybrid fact schema: `anchor/5` for metadata, `claim/4` for entity triples.
   - Heuristic text decomposition into subject-predicate-object triples.
   - Wire as Spring `@Service`.
   - Files: `AnchorPrologProjector.java`, `PrologFactSchema.java` (constants/helpers), test.

2. **Task 2: Prolog rule definitions** (3 files)
   - Define contradiction rules: negation-based, incompatible states.
   - Define default negation lookup table and incompatibility rules as Prolog facts.
   - Define invariant checking rules for authority floor and eviction immunity.
   - Package rules so they are loadable by `AnchorPrologProjector`.
   - Files: Prolog rule resource files, `PrologRuleLoader.java`, test.

3. **Task 3: LOGICAL conflict detection strategy** (4 files)
   - Implement `PrologConflictDetector` that uses `AnchorPrologProjector` to project anchors and query for contradictions.
   - Replace `UnsupportedOperationException` in `CompositeConflictDetector.LOGICAL` branch with delegation to `PrologConflictDetector`.
   - Support both `detect()` and `batchDetect()` methods.
   - Files: `PrologConflictDetector.java`, `CompositeConflictDetector.java` (modify LOGICAL branch), `AnchorConfiguration.java` (wire detector), test.

4. **Task 4: Prolog audit pre-filter** (4 files)
   - Implement `PrologAuditPreFilter` that projects active anchors and queries for contradiction chains and unsupported claims.
   - Integrate with `ProactiveMaintenanceStrategy` audit step -- anchors flagged by Prolog get score 0.0.
   - Toggleable via configuration property.
   - Files: `PrologAuditPreFilter.java`, `ProactiveMaintenanceStrategy.java` (pre-filter integration), `DiceAnchorsProperties.java` (toggle property), test.

5. **Task 5: Prolog invariant enforcer** (4 files)
   - Implement `PrologInvariantEnforcer` as a `ComplianceEnforcer`.
   - Project active anchors to Prolog facts, load invariant rules, query for violations.
   - Return `ComplianceResult` with violation details.
   - Files: `PrologInvariantEnforcer.java`, `AnchorConfiguration.java` or assembly config (wire as bean), `DiceAnchorsProperties.java` (enforcer selection), test.

6. **Task 6: A/B testability wiring** (5 files)
   - Add scenario YAML configuration for Prolog strategy selection (conflict detection strategy, audit pre-filter toggle, compliance enforcer selection).
   - Wire strategy selection from scenario config into simulation run context.
   - Ensure strategy selection propagates to all three Prolog implementations.
   - Files: scenario YAML schema update, `ScenarioLoader.java`, `SimulationRunContext.java`, `AnchorConfiguration.java` or strategy factory, test.

7. **Task 7: Tests and verification** (4 files)
   - A/B comparison tests: same anchor set processed by Prolog vs. non-Prolog strategies.
   - Performance tests: query latency < 100ms for 20-anchor sets.
   - Integration test: full simulation run with LOGICAL strategy enabled.
   - Files: `PrologConflictDetectorTest.java`, `PrologAuditPreFilterTest.java`, `PrologInvariantEnforcerTest.java`, `AnchorPrologProjectorTest.java`.

## Gates

Implementation is complete when ALL of the following are satisfied:

1. **LOGICAL strategy works**: `CompositeConflictDetector` with `ConflictDetectionStrategy.LOGICAL` MUST produce conflict detection results without throwing exceptions.
2. **A/B comparison passes**: At least one Prolog-backed implementation MUST produce results comparable to its non-Prolog counterpart on a shared test scenario.
3. **Performance**: All Prolog queries MUST complete in < 100ms for 20-anchor sets.
4. **Projection works**: `AnchorPrologProjector` MUST successfully project any valid anchor set to Prolog facts and return results via `PrologEngine` queries.
5. **Simulator selection**: The simulator MUST be able to select Prolog vs. non-Prolog strategies per scenario YAML without code changes.
6. **No regression**: Existing simulation scenarios MUST produce equivalent or better results with Prolog implementations available (but not necessarily selected).
7. **Observability**: Prolog projection and query events MUST appear in logs at INFO level.

## Dependencies Map

```
F03 (compliance-enforcement-layer) ──┐
F05 (precomputed-conflict-index) ────┼──► F13 (prolog-integration-layer)
F07 (proactive-maintenance-cycle) ───┘
```

F13 consumes:
- `ComplianceEnforcer` interface from F03
- `ConflictDetectionStrategy.LOGICAL` enum value + `CompositeConflictDetector` switch branch from F05
- `ProactiveMaintenanceStrategy` pre-filter hook from F07

F13 integrates with (no dependency):
- `AnchorPrologProjector` (new, shared projection foundation)
- `InvariantEvaluator` / `InvariantRuleProvider` (existing, for Prolog invariant rule alignment)
- `DomainProfile` (existing, for rule extensibility)
- `DiceAnchorsProperties` (existing, for configuration)
- DICE `PrologEngine` (existing via DICE 0.1.0-SNAPSHOT -- tuProlog/2p-kt already on classpath, zero new dependencies)
