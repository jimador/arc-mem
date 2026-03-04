# Dependency Research: Anchor Memory Optimization Roadmap

**Date**: 2026-03-03
**Context**: Evaluating 3rd-party libraries needed for the 11 active features in the anchor-memory-optimization roadmap.

---

## Summary

Most features can be implemented with libraries already on the classpath. Only 1–2 new dependencies are needed. One high-value capability — DICE Prolog projection — is already available via the DICE 0.1.0-SNAPSHOT dependency and could serve multiple roadmap features.

| Status | Count | Libraries |
|--------|-------|-----------|
| **Already available** | 5 | Guava (transitive), Micrometer (transitive), Neo4j vector indexes, AttentionWindow pattern, **DICE Prolog (tuProlog/2p-kt)** |
| **Recommended to add** | 2 | Caffeine, spring-boot-starter-actuator |
| **Future consideration** | 2 | Apache Commons Math 3.6.1 (F10), DJL + ONNX Runtime (F03 advanced) |
| **Not needed** | 11 | JGraphT, JUNG, Eclipse Collections, Guava Cache, SMILE, Stanford CoreNLP, Weka, Spring Batch, Timefold, Drools/KIE, Evrete |

---

## Feature-by-Feature Recommendations

### F02: Unified Maintenance Strategy
**Dependencies needed: none.** Pure interface/strategy refactoring of existing `DecayPolicy` and `ReinforcementPolicy`.

### F03: Compliance Enforcement Layer
**Dependencies needed: none (initial implementation).**

The `PostGenerationValidator` (first strategy) uses the existing LLM infrastructure for contradiction checking — same pattern as `LlmConflictDetector`.

**Future option — DJL + ONNX Runtime for local NLI:**

| Field | Value |
|-------|-------|
| Maven | `ai.djl:api:0.35.1` + `ai.djl.onnxruntime:onnxruntime-engine:0.35.1` |
| License | Apache 2.0 |
| Java 25 | Yes |
| Last release | December 2025 |

A small NLI cross-encoder model (~82M params, ~330MB ONNX file) could provide a mid-tier between lexical negation detection (fast/crude) and full LLM calls (slow/accurate). Inference is ~5-15ms per pair on CPU. Would slot into `CompositeConflictDetector` as a new strategy. **Defer until post-generation validation proves insufficient.**

**Skipped:**
- Stanford CoreNLP — GPL license, 482MB footprint, minimal incremental value over existing negation detector
- OpenNLP — no entailment/contradiction capability, only preprocessing

### F04: Memory Pressure Gauge
**Dependencies needed: spring-boot-starter-actuator (optional).**

**Micrometer** is already a transitive dependency (60+ usages via `@Observed`, `ObservationRegistry`). Provides:
- `Gauge` — tracks computed pressure score on demand
- `Counter` — monotonically increasing event counts (conflicts, demotions, compactions)
- `DistributionSummary` — sliding window via `expiry(Duration)` + `bufferLength(int)`

**Existing `AttentionWindow` pattern** already implements sliding-window event rate computation with burst factor calculation. Can be composed into the pressure gauge directly.

**spring-boot-starter-actuator** would expose metrics at `/actuator/metrics` for debugging. Not strictly required but useful. Version managed by Spring Boot BOM:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Skipped:**
- Dropwizard Metrics — Micrometer is the Spring Boot standard, already present

### F05: Precomputed Conflict Index
**Dependencies needed: none.**

**Decision: Use Neo4j relationships for conflict adjacency.** The project already models all anchor state as Neo4j graph data via Drivine. Conflict relationships are a natural graph edge:

```
(a:Proposition)-[:CONFLICTS_WITH {confidence: 0.95, reason: "...", detectedAt: datetime()}]->(b:Proposition)
```

Advantages over in-memory structures:
- Persists across restarts (no cold-start rebuild)
- Leverages existing Drivine ORM and `AnchorRepository` patterns
- Queryable via Cypher: `MATCH (a)-[:CONFLICTS_WITH]->(b) WHERE a.id = $anchorId RETURN b`
- Cleaned up automatically with context isolation (`clearByContext`)
- No new dependency, no cache coherence concerns

The `ConflictIndex` interface queries Neo4j on lookup and writes relationships on lifecycle events. For hot-path performance, the Caffeine cache (F11) can optionally cache conflict lookups — but Neo4j is the source of truth.

**Skipped:**
- JGraphT (1.5.2, LGPL) — overkill; graph algorithms not needed for adjacency lookup. Revisit for F10 if needed.
- JUNG — abandoned (last release 2016)
- Apache Commons Graph — does not exist as a viable library
- Guava `common.graph` — no concurrent access support, thin abstraction
- `ConcurrentHashMap` roll-your-own — unnecessary given Neo4j is already the persistence layer

### F06: Promotion Pipeline Optimization
**Dependencies needed: none.**

**Guava `Lists.partition()`** is already available as a transitive dependency (via Embabel Agent). Provides exactly what F06 needs for fixed-size batch splitting. Also available: `Iterables.paddedPartition()` for strict fixed-size with null padding.

**Skipped:**
- Apache Commons Collections — functionally identical to Guava's partition, not already available
- Spring Batch — massive overkill for list partitioning

### F07: Proactive Maintenance Cycle
**Dependencies needed: none.** Consumes F02 (maintenance strategy) and F04 (pressure gauge). Pure domain logic.

### F08: Proposition Quality Scoring
**Dependencies needed: none.**

**Neo4j vector indexes** are already configured and used in `AnchorRepository`:
- `semanticSearch(text, contextId, topK, threshold)` — returns similar propositions with cosine similarity scores
- `findSimilarWithScores()` — vector-indexed similarity search

For **novelty scoring**: Query semantic search. High similarity to existing anchors = low novelty. The inverse of max similarity score is a novelty signal. No new dependency.

For **importance scoring**: Conversation-topic relevance via the same embedding infrastructure.

For **in-memory cosine similarity** (if needed to avoid DB round-trips): A 10-line inline utility method. The existing `GraphConsistencySignal` already implements Jaccard similarity inline — same pattern.

**Skipped:**
- Apache Commons Math 3.6.1 — overkill for a single `cosine()` call (library is 300+ classes, EOL)
- SMILE 5.2.0 — GPL license, massive ML framework for a single function

### F09: Adaptive Prompt Footprint
**Dependencies needed: none.** Template changes in `AnchorsLlmReference` and `PromptBudgetEnforcer`. Pure domain logic.

### F10: Interference-Density Budget
**Dependencies needed: Apache Commons Math 3.6.1 (when implemented).**

| Field | Value |
|-------|-------|
| Maven | `org.apache.commons:commons-math3:3.6.1` |
| License | Apache 2.0 |
| Java 25 | Yes (pure Java) |
| Size | ~1.5MB |
| Last release | 2016 (EOL but stable) |

Provides `DBSCANClusterer` for density-based clustering of anchor embeddings:
```java
var clusterer = new DBSCANClusterer<>(epsilon, minPts);
List<Cluster<DoublePoint>> clusters = clusterer.cluster(points);
```

DBSCAN is ideal for interference density because it finds dense regions without requiring a predefined cluster count, handles noise (isolated anchors) naturally.

**Alternative: Neo4j GDS plugin** (Louvain/Leiden community detection, K-Means) — runs server-side, no Java dependency, but requires plugin installation in the Neo4j instance.

**Skipped:**
- SMILE — GPL/LGPL license concerns
- Weka — GPL, heavy (full ML workbench)

### F11: Tiered Anchor Storage
**Dependencies needed: Caffeine.**

| Field | Value |
|-------|-------|
| Maven | `com.github.ben-manes.caffeine:caffeine` (version managed by Spring Boot BOM = 3.2.3) |
| License | Apache 2.0 |
| Java 25 | Yes |
| Size | ~600KB |

Caffeine is the standard Java in-memory cache, written by the same author as Guava Cache's predecessor. Spring Boot 3.5.10 manages the version — no `<version>` tag needed:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Provides everything F11's HOT tier needs:
- O(1) lookup by key (`Cache<String, Anchor>`)
- Size-bounded eviction (`maximumSize(n)`) with W-TinyLFU admission policy
- TTL safety net (`expireAfterWrite`)
- Removal listeners for observability
- Lock-free concurrent reads
- Built-in stats (`recordStats()`) for cache hit/miss metrics

**Why not ConcurrentHashMap?** The project already uses `ConcurrentHashMap` for caching, but it lacks size-bounded eviction, TTL, and eviction listeners. Adding these manually would reimplement Caffeine poorly.

**Skipped:**
- Guava Cache — superseded by Caffeine in every dimension. Same author. Deprecated Spring integration.
- Eclipse Collections — overkill for current data sizes; JDK collections sufficient
- Spring Cache abstraction (`@Cacheable`) — F11 needs structural caching (a `TieredAnchorRepository` decorator), not method-level annotation caching. Caffeine is used programmatically.

### F12: Constraint-Aware Decoding
**Dependencies needed: TBD (deferred feature).** Will depend on model hosting infrastructure when implemented. The `ComplianceEnforcer` interface (F03) is dependency-free.

---

## Cross-Cutting Framework Research

### DICE Prolog Projection (tuProlog / 2p-kt)

**Status: Already on classpath. High-value opportunity for multiple features.**

DICE 0.1.0-SNAPSHOT includes experimental Prolog projection via tuProlog (2p-kt). Propositions are projected to Prolog facts; queries run against the resulting knowledge base.

| Field | Value |
|-------|-------|
| Engine | tuProlog 2p-kt (Kotlin multiplatform) |
| How available | Transitive via `dev.dunnam:dice:0.1.0-SNAPSHOT` |
| License | Apache 2.0 |
| Key API | `PrologEngine.query()`, `queryAll()`, `queryFirst()`, `findAll()` |
| Current usage in dice-anchors | **None** — zero Prolog usage today |

**Why this matters for the roadmap:** Prolog's backward chaining and logical inference are a natural fit for several anchor operations that currently require LLM calls or hand-coded Java logic:

| Feature | Prolog Application | Current Approach | Prolog Advantage |
|---------|--------------------|------------------|-----------------|
| F03 (Compliance) | Express anchor invariants as Prolog rules; query for violations deterministically | `InvariantEvaluator` with hand-coded Java checks | Declarative rules, trivially extensible, zero LLM cost |
| F05 (Conflict Index) | Logical contradiction detection via Prolog rules (complement to LLM semantic detection) | `NegationConflictDetector` (lexical) + `LlmConflictDetector` (semantic) | Deductive reasoning catches logical contradictions that lexical negation misses and LLM calls are too expensive for |
| F07 (Proactive Maintenance) | Audit anchor consistency via Prolog queries against the full proposition set | LLM-batched relevance audit | Fast, deterministic consistency checks as a pre-filter before LLM audit |
| F08 (Quality Scoring) | Derive proposition relationships (entailment, support, subsumption) via inference | Ad-hoc Java scoring logic | Composable scoring rules, transitive relationship discovery |
| F10 (Interference Density) | Query for dense conflict clusters via transitive closure | DBSCAN clustering (Apache Commons Math) | Graph-native cluster detection without external library |

**Recommendation: Invest in DICE Prolog integration as a cross-cutting capability.** It serves 5+ roadmap features, requires zero new dependencies, and aligns with the DICE integration goal. The investment is in writing Prolog rules and projection mappings, not in adding libraries.

**Approach:**
1. Project anchor propositions to Prolog facts (text, authority, rank, conflict relationships).
2. Define rule sets per feature (invariant rules, contradiction rules, consistency rules).
3. Use `PrologEngine` queries as a fast, deterministic pre-filter before expensive LLM calls.
4. Rules are extensible per `DomainProfile` — domain-specific Prolog rules loaded at context creation.

**Risk:** tuProlog 2p-kt is experimental in DICE. If the projection API changes, rules need updating. Mitigation: wrap Prolog access behind a dice-anchors interface (`PrologReasoner` or similar) so the projection mechanism is swappable.

### Timefold Solver

**Status: Not recommended for current scope. Premature for ~20 anchor problem size.**

| Field | Value |
|-------|-------|
| Maven | `ai.timefold.solver:timefold-solver-spring-boot-starter` |
| License | Apache 2.0 |
| Java 25 | Yes (verified) |
| Spring Boot | Starter available |

Timefold is a constraint satisfaction/optimization solver (fork of OptaPlanner). It excels at multi-objective optimization with complex constraints — e.g., "maximize anchor coverage while minimizing conflict density and staying within token budget."

**Why not now:** The current anchor budget (~20 active anchors) is small enough that greedy algorithms (sort by rank, evict lowest) are near-optimal. Timefold's value emerges when:
- Budget optimization becomes multi-dimensional (coverage + conflict density + token cost + diversity)
- Anchor counts grow beyond where greedy heuristics are sufficient
- Constraint interactions become non-trivial (satisfying one constraint violates another)

**Revisit trigger:** If F09 (Adaptive Prompt Footprint) or F10 (Interference Density) reveal that greedy budget allocation produces measurably suboptimal results, Timefold becomes the natural solver.

### Drools / KIE Rule Engine

**Status: Not recommended. Too heavy for current needs.**

| Field | Value |
|-------|-------|
| Maven | `org.kie:kie-spring-boot-starter-rules:10.1.0` |
| License | Apache 2.0 |
| Java 25 | Yes |
| Size | ~30 JARs, significant classpath footprint |

Drools is a production rule engine with RETE-based forward chaining. While powerful, it adds substantial complexity (DRL rule language, knowledge sessions, rule compilation) for problems that Java's sealed interfaces + pattern matching + switch expressions already handle cleanly.

**Lightweight alternative evaluated — Evrete:**

| Field | Value |
|-------|-------|
| Maven | `org.evrete:evrete-core:4.1.02` |
| License | MIT |
| Size | Zero transitive dependencies, ~200KB |
| Engine | RETE algorithm, Java-native rule definitions |

Evrete is a minimal RETE engine that defines rules in plain Java (no DSL). However, since DICE Prolog is already available and provides backward chaining (which Drools/Evrete cannot), the Prolog path is strictly more capable for the roadmap's needs.

**Why Prolog wins over rule engines for this project:**
- Prolog supports backward chaining (goal-directed queries) — rule engines are forward chaining only
- Prolog is already on the classpath via DICE
- Prolog rules compose naturally with DICE's proposition model
- Rule engines add a new execution model (knowledge sessions, rule compilation) with no offsetting advantage

---

## Dependencies to Add (Maven)

### Immediate (Wave 1-2)

```xml
<!-- F11: HOT tier anchor cache (version managed by Spring Boot 3.5.10 = 3.2.3) -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- F04: Expose memory pressure metrics via /actuator/metrics (optional) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Future (Wave 4)

```xml
<!-- F10: DBSCAN clustering for interference density (when F10 is implemented) -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
</dependency>
```

---

## Already-Available Libraries by Feature

| Feature | Library | How Available | Key API |
|---------|---------|---------------|---------|
| F03, F05, F07, F08, F10 | **DICE Prolog (tuProlog/2p-kt)** | Transitive (DICE 0.1.0-SNAPSHOT) | `PrologEngine.query()`, `queryAll()`, `findAll()` |
| F04 | Micrometer | Transitive (Spring Boot) | `Gauge`, `Counter`, `DistributionSummary` |
| F04 | AttentionWindow | Existing codebase | Sliding window event rate |
| F05 | Neo4j / Drivine | Direct dependency | `CONFLICTS_WITH` relationships, Cypher queries |
| F06 | Guava | Transitive (Embabel) | `Lists.partition()`, `Iterables.paddedPartition()` |
| F08 | Neo4j vector index | Direct dependency | `semanticSearch()`, cosine similarity |
| F08 | GraphConsistencySignal | Existing codebase | Jaccard similarity pattern |
| All | ConcurrentHashMap | JDK | Thread-safe maps (existing pattern) |

---

## Explicitly Rejected Libraries

| Library | Reason | Feature |
|---------|--------|---------|
| Guava Cache | Superseded by Caffeine | F11 |
| JGraphT 1.5.2 | Overkill — Neo4j handles adjacency natively | F05 |
| JUNG 2.1.1 | Abandoned (last release 2016) | F05 |
| Eclipse Collections 13.0.0 | Overkill for current data sizes | F11 |
| SMILE 5.2.0 | GPL/LGPL license | F08, F10 |
| Stanford CoreNLP | GPL, 482MB, minimal value over existing negation detector | F03 |
| Weka 3.8.6 | GPL, heavy ML workbench | F10 |
| Spring Batch | Massive overkill for list partitioning | F06 |
| Dropwizard Metrics | Micrometer is the Spring Boot standard | F04 |
| Apache Commons Collections | Guava partition already available | F06 |
| Apache Commons Math (for F08) | Overkill for single cosine function | F08 |
| Timefold Solver | Premature — greedy algorithms near-optimal for ~20 anchors | F09, F10 |
| Drools / KIE 10.1.0 | Too heavy (~30 JARs); DICE Prolog provides backward chaining already | F03, F07 |
| Evrete 4.1.02 | Forward chaining only; Prolog is strictly more capable and already available | F03, F07 |
