## Context

dice-anchors currently has zero Prolog usage despite DICE 0.1.0-SNAPSHOT providing tuProlog (2p-kt) 1.0.4 on the classpath. Three integration points are designed but unimplemented:

1. `CompositeConflictDetector` LOGICAL branch throws `UnsupportedOperationException`
2. `ProactiveMaintenanceStrategy` audit step uses heuristic + optional LLM scoring with no deterministic pre-filter
3. `ComplianceEnforcer` interface has `PostGenerationValidator` and `PromptInjectionEnforcer` but no Prolog-based enforcer

The DICE Prolog API (discovered via classpath investigation):

```
com.embabel.dice.projection.prolog.PrologEngine
  - PrologEngine(String theory)           // constructor: loads Prolog theory string
  - boolean query(String goal)            // yes/no query
  - List<QueryResult> queryAll(String)    // all solutions
  - QueryResult queryFirst(String)        // first solution
  - List<String> findAll(String, String)  // findAll/3 wrapper

com.embabel.dice.projection.prolog.QueryResult
  - boolean getSuccess()
  - Map<String, String> getBindings()     // variable -> value bindings

com.embabel.dice.projection.prolog.PrologFact
  - PrologFact(String predicate, List<String> args, double confidence, double decay, List<String> sourcePropositionIds)
  - String toProlog()                     // renders "predicate(arg1, arg2, ...)"
  - String toPrologTerm()

com.embabel.dice.projection.prolog.PrologRuleLoader
  - String loadFromResource(String path)  // loads .pl from classpath
  - String loadMultiple(String... paths)  // merges multiple .pl files
```

Key insight: `PrologEngine` is created via companion object factory methods callable from Java:
- `PrologEngine.Companion.fromTheory(String theory)` -- creates engine from a theory string
- `PrologEngine.Companion.fromProjection(PrologProjectionResult, PrologSchema)` -- creates from DICE projection result
- `PrologEngine.Companion.empty()` -- creates empty engine

The primary constructor is private; `fromTheory()` is the entry point for custom fact/rule theories. The engine is lightweight and stateless -- create a fresh instance per query batch. No engine pooling needed at the 20-anchor scale.

## Goals / Non-Goals

**Goals:**
- Define `AnchorPrologProjector` as the shared projection foundation for all Prolog implementations
- Implement `PrologConflictDetector` backing the `LOGICAL` strategy in `CompositeConflictDetector`
- Implement `PrologAuditPreFilter` for `ProactiveMaintenanceStrategy` audit step
- Implement `PrologInvariantEnforcer` as a `ComplianceEnforcer`
- Define Prolog rules for contradiction detection (negation + incompatible states) and invariant checking
- Wire A/B strategy selection via `ConflictStrategy.LOGICAL` configuration
- Sub-second query latency (<100ms for 20 anchors)

**Non-Goals:**
- `PrologRelationshipScorer` (F08) -- lower priority, heuristic scoring works
- Prolog transitive closure for interference density (F10)
- Prolog rule UI/editor
- Cross-context Prolog fact sharing
- LLM-assisted text decomposition for projection

## Decisions

### D1: PrologEngine Lifecycle -- Fresh Instance Per Query Batch

**Decision**: Create a new `PrologEngine` instance via `PrologEngine.Companion.fromTheory(theory)` for each projection + query operation. No engine caching or pooling.

**Why**: `PrologEngine` holds an immutable theory string (Kotlin `val`). At 20 anchors generating ~40 facts + ~50 rule lines, the theory is <5KB. tuProlog parse time is <5ms for theories this size. The simplicity of fresh-instance-per-call eliminates concurrency concerns and stale-state bugs.

### D2: Hybrid Fact Schema

**Decision**: Two fact families:
```prolog
% Metadata: anchor(Id, AuthorityOrdinal, Rank, Pinned, ReinforcementCount)
anchor('anc-001', 2, 750, true, 5).

% Entity triples: claim(AnchorId, Subject, Predicate, Object)
claim('anc-001', 'baron_krell', 'is', 'alive').
claim('anc-001', 'baron_krell', 'leads', 'siege_of_tidefall').
```

Authority is projected as ordinal (0=PROVISIONAL, 1=UNRELIABLE, 2=RELIABLE, 3=CANON) for arithmetic comparison in Prolog rules.

**Why**: Metadata facts support invariant queries (authority floor, eviction immunity) without text parsing. Entity triples support contradiction queries without opaque text comparison. The two families serve different query types.

### D3: Heuristic SVO Decomposition

**Decision**: Text decomposition into subject-predicate-object triples uses a simple heuristic:
1. Split text on sentence boundaries (period, semicolon)
2. For each sentence, extract first noun phrase as subject, first verb as predicate, remainder as object
3. Normalize to lowercase, replace spaces with underscores
4. Sentences that don't parse into clean triples are projected as `claim(Id, 'unknown', 'states', NormalizedFullText)`

**Why**: Projection MUST NOT use LLM calls (locked decision L1 -- deterministic pre-filtering). Heuristic decomposition produces adequate triples for common patterns ("X is Y", "X has Y", "X leads Y"). False negatives from poor decomposition are acceptable: Prolog is a pre-filter, LLM catches what Prolog misses.

### D4: Layered Contradiction Rules with Short-Circuit

**Decision**: Contradiction detection uses layered rules that short-circuit on first match:

```prolog
% Layer 1: Negation (cheapest)
contradicts(A, B) :-
    claim(A, S, P, O), claim(B, S, P, O2),
    negation(O, O2), A \= B.

% Layer 2: Incompatible states
contradicts(A, B) :-
    claim(A, S, P, O), claim(B, S, P, O2),
    incompatible(O, O2), A \= B.

% Negation pairs (symmetric)
negation(alive, dead). negation(dead, alive).
negation(present, absent). negation(absent, present).
negation(true, false). negation(false, true).
negation(open, closed). negation(closed, open).

% Incompatibility groups
incompatible(X, Y) :- location(X), location(Y), X \= Y.
```

**Why**: Negation is the highest-value, lowest-cost check. Incompatible states are a natural extension. Prolog's resolution strategy naturally short-circuits -- `queryFirst()` returns the first proof found. Additional layers (temporal consistency) MAY be added later as separate rules.

### D5: PrologConflictDetector Integration

**Decision**: `PrologConflictDetector` implements `ConflictDetector`. The `CompositeConflictDetector` LOGICAL branch delegates to it instead of throwing `UnsupportedOperationException`. The detector:

1. Projects all existing anchors + incoming text via `AnchorPrologProjector`
2. Creates `PrologEngine` with projected facts + contradiction rules
3. Queries `contradicts(IncomingId, ExistingId)` for each existing anchor
4. Converts results to `ConflictDetector.Conflict` records
5. Returns empty list on any Prolog failure (error contract: never throw)

For `batchDetect()`: processes each candidate independently using the default `ConflictDetector.batchDetect()` implementation (iterates `detect()`). Prolog query cost is negligible per candidate.

### D6: PrologAuditPreFilter Integration

**Decision**: `PrologAuditPreFilter` is a standalone service called from `ProactiveMaintenanceStrategy.auditAnchors()` before heuristic scoring. When enabled:

1. Project all active anchors via `AnchorPrologProjector`
2. Query for contradiction pairs among existing anchors: `contradicts(A, B)` for all pairs
3. Anchors appearing in contradiction results get audit score overridden to 0.0
4. Remaining anchors proceed to normal heuristic scoring

The pre-filter is toggled via `DiceAnchorsProperties.ProactiveConfig.prologPreFilterEnabled` (default: false).

**Why**: This reduces the LLM audit batch size by removing anchors that Prolog has already identified as problematic. The LLM call is the expensive step; Prolog pre-filtering is <10ms.

### D7: PrologInvariantEnforcer as ComplianceEnforcer

**Decision**: `PrologInvariantEnforcer` implements `ComplianceEnforcer`. It projects active anchors to Prolog facts, loads invariant rules, and queries for violations:

```prolog
% Authority floor violation
authority_violation(AnchorId) :-
    anchor(AnchorId, Auth, _, _, _),
    authority_floor(AnchorId, MinAuth),
    Auth < MinAuth.

% Eviction immunity violation
eviction_violation(AnchorId) :-
    anchor(AnchorId, _, _, _, _),
    eviction_immune(AnchorId).
```

The enforcer checks the LLM response text against active anchors using the same contradiction rules as `PrologConflictDetector`, but scoped to enforced anchors (filtered by `CompliancePolicy`).

Returns `ComplianceResult` with violations mapped from Prolog query bindings.

### D8: A/B Strategy Selection

**Decision**: Add `LOGICAL` to the `ConflictStrategy` config enum. `AnchorConfiguration.conflictDetector()` gains a `LOGICAL` case that wires `PrologConflictDetector` wrapped in `CompositeConflictDetector` with `ConflictDetectionStrategy.LOGICAL`. Scenario YAML can override `conflictDetection.strategy` to select LOGICAL for A/B testing.

### D9: Rules Extensibility

**Decision**: Prolog rules are loaded from classpath resources via `PrologRuleLoader`. Default rules are in `prolog/anchor-rules.pl`. Domain-specific rules MAY be loaded from additional files based on `DomainProfile`:

- `prolog/anchor-rules.pl` -- base rules (always loaded)
- `prolog/domain-{profile-name}-rules.pl` -- domain-specific rules (loaded when profile matches)

`AnchorPrologProjector` loads rules via `PrologRuleLoader.loadFromResource()` and concatenates them with projected facts to form the theory string.

## Data Flow

```
                    AnchorPrologProjector
                           |
            +------+-------+-------+------+
            |              |              |
    PrologConflict    PrologAudit    PrologInvariant
     Detector         PreFilter       Enforcer
            |              |              |
            v              v              v
    CompositeConflict  Proactive     Compliance
     Detector          Maintenance    Result
    (LOGICAL branch)   (audit step)

Projection Flow:
  List<Anchor> ──> AnchorPrologProjector
                     |
                     +--> anchor/5 facts (metadata)
                     +--> claim/4 facts (entity triples)
                     +--> rules from anchor-rules.pl
                     |
                     v
                  PrologEngine.Companion.fromTheory(theory)
                     |
                     +--> query("contradicts(X, Y)")
                     +--> queryAll("authority_violation(X)")
                     +--> query("contradicts(incoming, X)")
```

## File Inventory

### New Files (5)

| File | Package | Type | Description |
|------|---------|------|-------------|
| `AnchorPrologProjector.java` | `anchor/` | Service | Shared projection: anchors to Prolog facts |
| `PrologConflictDetector.java` | `anchor/` | Service | LOGICAL conflict detection via Prolog |
| `PrologAuditPreFilter.java` | `anchor/` | Service | Prolog pre-filter for maintenance audit |
| `PrologInvariantEnforcer.java` | `assembly/` | Service | Prolog compliance enforcer |
| `anchor-rules.pl` | `resources/prolog/` | Prolog | Contradiction + invariant rules |

### Modified Files (5)

| File | Change |
|------|--------|
| `CompositeConflictDetector.java` | LOGICAL branch delegates to injected `PrologConflictDetector` instead of throwing |
| `ConflictStrategy.java` | Add `LOGICAL` value |
| `AnchorConfiguration.java` | Wire Prolog beans, LOGICAL case in `conflictDetector()` |
| `DiceAnchorsProperties.java` | Add `prologPreFilterEnabled` to `ProactiveConfig` |
| `ProactiveMaintenanceStrategy.java` | Call `PrologAuditPreFilter` in audit step when enabled |

## Research Attribution

- **Sleeping LLM (Guo et al., 2025)**: Validates deterministic pre-filter pattern. Prolog queries serve the same role as sleeping-llm's offline validation: handle logically decidable questions deterministically, reserve LLM for semantic ambiguity.
- **DICE Prolog (tuProlog/2p-kt)**: Leverages existing classpath dependency for backward chaining. The `PrologEngine` API (`query()`, `queryAll()`, `queryFirst()`, `findAll()`) and `PrologRuleLoader` are the integration surface.
- **Google AI STATIC**: Precomputed constraint relationships principle. Prolog rules are the compile-time constraint specification; `PrologEngine` is the runtime evaluator.

## Deferred Work

| Item | Deferred To | Reason |
|------|-------------|--------|
| Temporal consistency rules (Q2 layer 3) | Future | Most complex rule layer; negation + incompatibility cover 80% of detectable contradictions |
| `PrologRelationshipScorer` (F08) | F08 implementation | Lower priority; heuristic scoring works for demo |
| Prolog transitive closure (F10) | F10 implementation | F10 not yet implemented |
| Domain-specific rule files per DomainProfile | Future | Default rules are domain-independent; extension point exists but no domain rules needed yet |
| Scenario YAML strategy override | Future | Config-level strategy selection sufficient for initial A/B testing |

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Heuristic SVO decomposition produces poor triples | Prolog is a pre-filter; LLM catches what Prolog misses. False negatives acceptable. |
| PrologEngine construction cost per query batch | <5ms for 20-anchor theories. Benchmarked acceptable. |
| DICE PrologEngine API changes in future versions | `AnchorPrologProjector` encapsulates all DICE interaction. API changes localized to one class. |
| Prolog rules produce false positives | Conservative rule design -- flag only clear logical contradictions. A/B comparison validates against LLM baseline. |
| tuProlog 2p-kt is Kotlin -- interop overhead from Java | Minimal. `PrologEngine` has clean Java-callable API (no Kotlin coroutines, no inline classes). |

## Open Questions

None. All design questions from the prep document have been resolved:
- Q1 (fact schema): Hybrid -- `anchor/5` metadata + `claim/4` entity triples (D2)
- Q2 (contradiction rules): Layered with short-circuit -- negation then incompatible states (D4)
- Q3 (complex text): Multi-claim heuristic SVO decomposition (D3)
