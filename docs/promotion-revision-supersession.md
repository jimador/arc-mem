# Promotion, Revision, and Supersession

This document covers the three key anchor mutation workflows: promotion (proposition → anchor), revision (anchor text update), and supersession (anchor replacement with lineage tracking).

## Promotion Pipeline

`AnchorPromoter` (`src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java`) processes DICE propositions through a strict 5-gate sequence.

### Gate Sequence

```
Proposition → Confidence → Dedup → Conflict → Trust → Promote
```

**Gate 1: Confidence** — Drops propositions with confidence below `autoActivateThreshold` (default 0.65). Also filters by `PropositionStatus`: only `ACTIVE` or `PROMOTED` are candidates.

**Gate 2: Dedup** — `DuplicateDetector.isDuplicate(text, existingAnchors)`. In batch mode: exact-text cross-reference (no LLM), then intra-batch dedup, then LLM batch call. Falls back to per-candidate on failure.

**Gate 3: Conflict** — `AnchorEngine.detectConflicts(contextId, text)`. Two-pass resolution:
- Pass 1: compute all resolutions without side effects
- If any conflict has `DetectionQuality.DEGRADED`, route to review
- `KEEP_EXISTING` immediately rejects the incoming proposition
- Pass 2 (only if proposition survived): execute side effects
  - `REPLACE` → `engine.supersede(existing.id, prop.id, reason)`
  - `DEMOTE_EXISTING` → `engine.demote()` + `engine.reEvaluateTrust()`
  - `COEXIST` → `engine.reEvaluateTrust()`

**Gate 4: Trust** — `TrustPipeline.evaluate(node, contextId)` returns a `TrustScore` with `PromotionZone`:
- `AUTO_PROMOTE` → passes through
- `REVIEW` → skips auto-promotion (manual review required)
- `ARCHIVE` → skips entirely

**Gate 5: Promote** — `AnchorEngine.promote(id, initialRank, authorityCeiling)`. Writes the anchor with `PROVISIONAL` authority, then runs budget enforcement with invariant-aware eviction.

### Promotion Invariants

| ID | Invariant |
|----|-----------|
| P1 | Duplicate propositions MUST NOT produce separate anchors |
| P2 | Conflict resolution decisions MUST always be acted upon |
| P3 | Gate sequence is strictly ordered: confidence → dedup → conflict → trust → promote |
| P4 | Budget enforcement runs per-promote call inside `AnchorEngine`, not at batch level |

### Funnel Logging

After each run, the promoter logs counts at each stage: `total → post-confidence → post-dedup → post-conflict → post-trust → promoted → degraded-conflicts`.

---

## Revision Pipeline

Revision is the controlled update of an existing anchor's text when new information refines (but does not contradict) it. This is distinct from contradiction, where new information asserts the opposite.

### Conflict Type Classification

`ConflictType` (`src/main/java/dev/dunnam/diceanchors/anchor/ConflictType.java`):

| Type | Meaning | Example |
|------|---------|---------|
| `REVISION` | Incoming updates/refines existing anchor | "The dragon is actually an ancient red dragon" refining "There is a dragon" |
| `CONTRADICTION` | Incoming asserts the opposite | "There is no dragon" contradicting "There is a dragon" |
| `WORLD_PROGRESSION` | Narrative change, not a conflict | "The dragon flew away" (state change, not contradiction) |

### RevisionAwareConflictResolver

`RevisionAwareConflictResolver` (`src/main/java/dev/dunnam/diceanchors/anchor/RevisionAwareConflictResolver.java`) wraps `AuthorityConflictResolver` and intercepts REVISION and WORLD_PROGRESSION types.

Resolution logic:

```
if revision disabled → delegate to AuthorityConflictResolver
if WORLD_PROGRESSION → COEXIST (always)
if CONTRADICTION or no existing anchor → delegate to AuthorityConflictResolver
if REVISION:
    if MutationStrategy.evaluate() returns Deny → delegate
    CANON → KEEP_EXISTING (immutable)
    PROVISIONAL → REPLACE (always revisable)
    UNRELIABLE → REPLACE if confidence >= revisionConfidenceThreshold; else delegate
    RELIABLE → if !reliableRevisable: delegate; if confidence >= replaceThreshold: REPLACE; else delegate
```

### Authority-Gated Revision Eligibility

| Authority | Revision Eligible | Condition |
|-----------|-------------------|-----------|
| CANON | Never | Immutable; requires `CanonizationGate` for any change |
| RELIABLE | Configurable | `dice-anchors.anchor.revision.reliable-revisable` (default: false) |
| UNRELIABLE | Yes | When incoming confidence >= `dice-anchors.anchor.revision.confidence-threshold` (default: 0.75) |
| PROVISIONAL | Always | Unconditionally replaceable |

### AnchorMutationStrategy SPI

`AnchorMutationStrategy` (`src/main/java/dev/dunnam/diceanchors/anchor/AnchorMutationStrategy.java`) gates all mutation attempts:

```java
interface AnchorMutationStrategy {
    MutationDecision evaluate(MutationRequest request);
}
```

**MutationRequest** carries: `anchorId`, `revisedText`, `MutationSource` (UI / LLM_TOOL / CONFLICT_RESOLVER), `requesterId`.

**MutationDecision** (sealed):
- `Allow` — mutation proceeds
- `Deny(reason)` — mutation blocked
- `PendingApproval(requestId)` — queued for HITL approval

**HitlOnlyMutationStrategy** (default `@Service`):
- `UI` → `Allow`
- `LLM_TOOL` → `Deny` (LLM-initiated mutation disabled)
- `CONFLICT_RESOLVER` → `Deny` (conflict-resolver mutation disabled)

This means: with the default strategy, the `RevisionAwareConflictResolver` will never auto-replace anchors via the CONFLICT_RESOLVER source — it always falls through to `AuthorityConflictResolver`. Auto-replacement requires a different mutation strategy implementation.

### Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `dice-anchors.anchor.revision.enabled` | true | Enables `RevisionAwareConflictResolver` |
| `dice-anchors.anchor.revision.reliable-revisable` | false | Whether RELIABLE anchors can be auto-replaced |
| `dice-anchors.anchor.revision.confidence-threshold` | 0.75 | Gate for UNRELIABLE → REPLACE |
| `dice-anchors.conflict.replace-threshold` | 0.8 | Gate for RELIABLE → REPLACE (when enabled) |

---

## Supersession

Supersession records the lineage when one anchor replaces another. It creates a `SUPERSEDES` relationship in Neo4j linking successor to predecessor.

### AnchorEngine.supersede()

`AnchorEngine.supersede(predecessorId, successorId, reason)` (`src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java`):

1. Looks up predecessor node; logs WARN and returns if not found
2. Archives the predecessor via `archive(predecessorId, reason, successorId)`
3. For `REVISION` reason: if predecessor is still active after archive (fallback guard), forces archive via repository
4. Creates `SUPERSEDES` relationship in Neo4j via `repository.createSupersessionLink(successorId, predecessorId, supersessionReason)`
5. Publishes `AnchorLifecycleEvent.superseded`
6. For REVISION reason: writes a `TrustAuditRecord` with trigger "revision"
7. Sets OTel span attributes: `supersession.reason`, `.predecessor_id`, `.successor_id`, `.predecessor_authority`, `.predecessor_rank`

### SupersessionReason

`SupersessionReason` (`src/main/java/dev/dunnam/diceanchors/anchor/event/SupersessionReason.java`):

| Value | Meaning |
|-------|---------|
| `CONFLICT_REPLACEMENT` | Higher authority/confidence proposition replaced this one |
| `BUDGET_EVICTION` | Lowest-ranked anchor evicted during budget enforcement |
| `DECAY_DEMOTION` | Decay-triggered archival (rarely produces a SUPERSEDES link in practice) |
| `USER_REVISION` | User-intended revision via UI or tool |
| `MANUAL` | Explicit operator or system action |

Maps from `ArchiveReason` via `SupersessionReason.fromArchiveReason()`:

| ArchiveReason | SupersessionReason |
|---|---|
| CONFLICT_REPLACEMENT | CONFLICT_REPLACEMENT |
| BUDGET_EVICTION | BUDGET_EVICTION |
| DORMANCY_DECAY | DECAY_DEMOTION |
| REVISION | USER_REVISION |
| MANUAL | MANUAL |

### Neo4j Representation

`PropositionNode` carries two supersession fields:
- `supersededBy` — ID of the anchor that superseded this one (null if not superseded)
- `supersedes` — ID of the anchor that this one supersedes (null if no predecessor)

The `SUPERSEDES` relationship is a first-class Neo4j edge, enabling chain traversal.

### Chain Queries

`AnchorEngine` provides chain navigation:
- `findSupersessionChain(anchorId)` → ordered list from oldest to newest
- `findPredecessor(anchorId)` → `Optional<String>`
- `findSuccessor(anchorId)` → `Optional<String>`

### Current Limitations

- **1:1 only** — one successor replaces one predecessor. Fan-in (merge) and fan-out (split/refinement) are not yet modeled.
- **DECAY_DEMOTION** exists for completeness but may never produce a `SUPERSEDES` relationship in practice.
- **Naming inconsistency** — `ArchiveReason.DORMANCY_DECAY` maps to `SupersessionReason.DECAY_DEMOTION`.

---

## Decay and Demotion

`ExponentialDecayPolicy` reduces rank over time:

```
effectiveHalfLife = halfLifeHours / max(diceDecay, 0.01) * max(tierMultiplier, 0.01)
newRank = currentRank * 0.5^(hours / effectiveHalfLife)
```

Clamped to `[MIN_RANK, MAX_RANK]`. Pinned anchors are immune.

Rank-triggered authority demotion:
- RELIABLE demotes to UNRELIABLE when rank < `reliableRankThreshold` (default: 400)
- UNRELIABLE demotes to PROVISIONAL when rank < `unreliableRankThreshold` (default: 200)
- CANON is exempt (invariant A3b)
- Pinned anchors are exempt

---

## Reinforcement

`ThresholdReinforcementPolicy`:
- Rank boost: +50 per reinforcement
- Authority upgrade thresholds: 3 reinforcements → UNRELIABLE, 7 → RELIABLE
- CANON is never auto-assigned

After each reinforcement, `AnchorEngine` also calls `reEvaluateTrust(anchorId)` to update the trust score.

---

## Trust Re-evaluation

`AnchorEngine.reEvaluateTrust(anchorId)`:
1. Loads anchor from repository
2. Runs `TrustPipeline.evaluate(node, contextId)`
3. If trust score drops below `demoteThreshold` (default: 0.6), calls `demote(anchorId, TRUST_DEGRADATION)`
4. Appends a `TrustAuditRecord`
