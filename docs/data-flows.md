# Data Flows

End-to-end execution traces for the major ARC-Mem data paths. Each scenario shows the concrete sequence of service calls, state changes, and persistence operations so that a developer can trace any execution path without reading source code.

For structural diagrams (module map, component relationships, config defaults), see [architecture.md](architecture.md). For mutation semantics and gate details, see [promotion-revision-supersession.md](promotion-revision-supersession.md).

## 1. End-to-End: User Message → Memory Units → LLM Response

This is the complete path for the chat flow. A user sends a message, propositions are extracted, promoted to memory units, and assembled into the next LLM prompt.

```mermaid
sequenceDiagram
    actor User
    participant CV as ChatView
    participant CA as ChatActions
    participant LLM as LLM (model call)
    participant EXT as DICE Extraction
    participant DD as DuplicateDetector
    participant CD as ConflictDetector
    participant UP as SemanticUnitPromoter
    participant AE as ArcMemEngine
    participant TP as TrustPipeline
    participant DB as Neo4j
    participant REF as ArcMemLlmReference
    participant CE as ComplianceEnforcer

    User->>CV: send message
    CV->>CA: handle user turn

    Note over CA,REF: Prompt assembly (next turn context)
    CA->>REF: getContent()
    REF->>AE: inject(contextId)
    AE->>DB: load active units (rank > 0)
    DB-->>AE: List<MemoryUnit>
    AE-->>REF: sorted by rank desc
    REF-->>CA: formatted context block

    CA->>LLM: system prompt + context block + user message
    LLM-->>CA: model response

    Note over CA,CE: Post-generation compliance check
    CA->>CE: enforce(response, activeUnits, policy)
    CE-->>CA: ComplianceResult (ACCEPT/RETRY/REJECT)

    CA-->>User: display response

    Note over CA,DB: Async extraction and promotion
    CA->>EXT: extract propositions from conversation
    EXT-->>CA: List<Proposition>

    loop For each proposition
        CA->>DD: isDuplicate(text, existingUnits)
        alt duplicate
            Note over DD: skip
        else new
            CA->>CD: detect(text, existingUnits)
            alt conflict detected
                CD-->>CA: List<Conflict>
                Note over CA,AE: Build ResolutionContext from sourceIds + SourceAuthorityResolver
                CA->>AE: resolveConflict(conflict, resolutionContext)
                Note over AE: KEEP_EXISTING / REPLACE / COEXIST / DEMOTE_EXISTING
            end
            CA->>UP: promote candidate
            UP->>TP: evaluate(proposition, contextId)
            TP-->>UP: TrustScore (zone, ceiling)
            alt AUTO_PROMOTE
                UP->>AE: promote(id, initialRank, authorityCeiling)
                AE->>DB: persist + enforce budget
            else REVIEW / ARCHIVE
                Note over UP: hold or skip
            end
        end
    end
```

### Key observations

- Context assembly happens **before** the LLM call — the model sees the current state of working memory.
- Extraction and promotion happen **after** the response — new facts from the conversation are processed asynchronously.
- Compliance enforcement occurs **after** generation — the response is validated against active memory units.
- Budget enforcement runs **inside** `ArcMemEngine.promote()` — if capacity is exceeded, the lowest-ranked non-pinned unit is evicted immediately.

## 2. Scenario A: Promotion Trace

A new fact is extracted and promoted from PROVISIONAL through reinforcement to RELIABLE.

```mermaid
sequenceDiagram
    participant EXT as DICE Extraction
    participant UP as SemanticUnitPromoter
    participant AE as ArcMemEngine
    participant TP as TrustPipeline
    participant RP as ReinforcementPolicy
    participant DB as Neo4j

    Note over EXT: Turn 1: "The East Gate is breached"
    EXT->>UP: Proposition(text, confidence=0.85)
    UP->>TP: evaluate(proposition, contextId)
    TP-->>UP: TrustScore(0.72, AUTO_PROMOTE, ceiling=RELIABLE)
    UP->>AE: promote(id, rank=500, ceiling=RELIABLE)
    AE->>DB: persist as PROVISIONAL, rank=500

    Note over AE: Turns 2-4: fact mentioned again 3 times
    loop 3 reinforcements
        AE->>RP: checkPromotion(count, PROVISIONAL, ceiling=RELIABLE)
        AE->>DB: rank boost per reinforcement
    end
    RP-->>AE: promote to UNRELIABLE (threshold: 3)
    AE->>DB: authority = UNRELIABLE

    Note over AE: Turns 5-8: fact mentioned 4 more times
    loop 4 more reinforcements (total: 7)
        AE->>RP: checkPromotion(count, UNRELIABLE, ceiling=RELIABLE)
        AE->>DB: rank boost per reinforcement
    end
    RP-->>AE: promote to RELIABLE (threshold: 7)
    AE->>DB: authority = RELIABLE

    Note over AE: Cannot auto-promote to CANON (A3a invariant)
```

### State progression

| Turn | Reinforcements | Authority | Rank (approx) |
|------|---------------|-----------|----------------|
| 1 | 0 | PROVISIONAL | 500 |
| 2 | 1 | PROVISIONAL | 530 |
| 3 | 2 | PROVISIONAL | 560 |
| 4 | 3 | UNRELIABLE | 590 |
| 5 | 4 | UNRELIABLE | 620 |
| 6 | 5 | UNRELIABLE | 650 |
| 7 | 6 | UNRELIABLE | 680 |
| 8 | 7 | RELIABLE | 710 |

## 3. Scenario B: Conflict Resolution with REPLACE

An incoming fact contradicts an existing PROVISIONAL unit. The resolver chooses REPLACE, archiving the predecessor and linking the successor via supersession.

```mermaid
sequenceDiagram
    participant UP as SemanticUnitPromoter
    participant CD as CompositeConflictDetector
    participant CR as AuthorityConflictResolver
    participant AE as ArcMemEngine
    participant DB as Neo4j
    participant EVT as EventPublisher

    Note over UP: Existing: "Baron Krell has two arms" (PROVISIONAL, rank=450)
    Note over UP: Incoming: "Baron Krell has four arms" (confidence=0.9)

    UP->>CD: detect("Baron Krell has four arms", existingUnits)
    CD-->>UP: Conflict(existing=two-arms, confidence=0.88, type=SEMANTIC)

    Note over UP: Build ResolutionContext from sourceIds
    UP->>CR: resolve(conflict, resolutionContext)
    Note over CR: existing.authority = PROVISIONAL<br/>incoming.confidence = 0.9 > 0.8<br/>sourceRelation = UNKNOWN (fallback to authority logic)
    CR-->>UP: Resolution.REPLACE

    UP->>AE: supersede(predecessorId="two-arms", successorId="four-arms", reason=CONFLICT_REPLACEMENT)
    AE->>DB: archive "two arms" (rank=0, archived=true)
    AE->>DB: create SUPERSEDES edge (two-arms → four-arms)
    AE->>EVT: publish Archived(unitId, successorId, reason)
    AE->>EVT: publish AuthorityChanged(...)

    UP->>AE: promote("four-arms", rank=500, ceiling)
    AE->>DB: persist "four arms" as PROVISIONAL, rank=500
```

### Resolution decision matrix

| Existing Authority | Incoming Confidence | Resolution |
|-------------------|-------------------|------------|
| CANON or RELIABLE | any | KEEP_EXISTING |
| PROVISIONAL | > 0.8 | REPLACE |
| PROVISIONAL | ≤ 0.8 | COEXIST |
| UNRELIABLE | > 0.8 | REPLACE |
| UNRELIABLE | ≤ 0.8 | COEXIST |

## 4. Scenario C: Decay Demotion

A RELIABLE unit decays over several turns without reinforcement, eventually dropping below the demotion threshold.

```mermaid
sequenceDiagram
    participant MS as MaintenanceStrategy
    participant DP as DecayPolicy (Exponential)
    participant AE as ArcMemEngine
    participant DB as Neo4j
    participant EVT as EventPublisher

    Note over MS: Unit: "The village elder is trustworthy"<br/>Authority: RELIABLE, Rank: 700, diceDecay: 1.0

    Note over DP: Decay formula:<br/>effectiveHalfLife = halfLifeHours / max(diceDecay, 0.01) * tierMultiplier<br/>newRank = currentRank × 0.5^(hours / effectiveHalfLife)

    MS->>DP: decay(unit, turnsSinceReinforcement=5)
    DP-->>MS: newRank = 620
    MS->>AE: applyDecay(unitId, rank=620)
    AE->>DB: update rank = 620

    MS->>DP: decay(unit, turnsSinceReinforcement=10)
    DP-->>MS: newRank = 480
    MS->>AE: applyDecay(unitId, rank=480)
    AE->>DB: update rank = 480

    MS->>DP: decay(unit, turnsSinceReinforcement=15)
    DP-->>MS: newRank = 350
    Note over AE: Rank 350 < RELIABLE demotion threshold
    MS->>AE: applyDecay(unitId, rank=350)
    AE->>AE: demote(unitId, reason=DECAY)
    AE->>DB: authority = UNRELIABLE, rank = 350
    AE->>EVT: publish AuthorityChanged(RELIABLE → UNRELIABLE, reason=DECAY)

    Note over AE: A3b: if this were CANON, demotion would NOT occur
    Note over AE: A3d: if this were pinned, demotion would NOT occur
```

### Decay modifiers

| Factor | Effect |
|--------|--------|
| `diceDecay = 0.0` | No decay (permanent) |
| `diceDecay = 1.0` | Standard decay rate |
| `diceDecay > 1.0` | Accelerated decay |
| `memoryTier = HOT` | Slower decay (higher tier multiplier) |
| `memoryTier = COLD` | Faster decay (lower tier multiplier) |

## 5. Scenario D: Canonization via HITL

An operator explicitly promotes a RELIABLE unit to CANON through the `CanonizationGate`.

```mermaid
sequenceDiagram
    actor Operator
    participant UI as ChatView / MemoryUnitMutationTools
    participant CG as CanonizationGate
    participant AE as ArcMemEngine
    participant INV as InvariantEvaluator
    participant DB as Neo4j
    participant EVT as EventPublisher

    Note over UI: Unit: "The East Gate is breached"<br/>Authority: RELIABLE, Rank: 800

    Operator->>UI: request canonization (unitId)
    UI->>CG: requestCanonization(unitId, contextId)

    CG->>INV: evaluate(unit, domainRules)
    INV-->>CG: no violations

    alt autoApprovePromotions = true
        CG->>AE: promote to CANON
    else autoApprovePromotions = false
        CG-->>UI: requires HITL approval
        Operator->>CG: approve
        CG->>AE: promote to CANON
    end

    AE->>DB: authority = CANON
    AE->>EVT: publish AuthorityChanged(RELIABLE → CANON, reason=CANONIZATION)

    Note over AE: A3a: CANON is NEVER auto-assigned
    Note over AE: A3b: once CANON, immune to auto-demotion
    Note over AE: Decanonization requires separate HITL approval
```

### CANON guarantees

- Immune to automatic demotion (A3b)
- Immune to decay-triggered authority changes
- Highest priority in conflict resolution (KEEP_EXISTING)
- Minimal token footprint in adaptive prompt assembly (reference only)
- Decanonization requires explicit HITL approval through `CanonizationGate`

## 6. Scenario E: Supersession with Lineage Chain

A memory unit is superseded by a newer version. The predecessor is archived and linked to the successor, forming a queryable lineage chain.

```mermaid
sequenceDiagram
    participant Caller
    participant AE as ArcMemEngine
    participant DB as Neo4j
    participant EVT as EventPublisher

    Note over AE: Predecessor: "The bridge is intact" (id=u1, UNRELIABLE)
    Note over AE: Successor: "The bridge is destroyed" (id=u2, PROVISIONAL)

    Caller->>AE: supersede(predecessorId=u1, successorId=u2, reason=CONFLICT_REPLACEMENT)

    AE->>DB: archive u1 (rank=0, archived=true)
    AE->>DB: CREATE (u1)-[:SUPERSEDES]->(u2)
    AE->>EVT: publish Archived(u1, successorId=u2, reason=CONFLICT_REPLACEMENT)

    Note over DB: Lineage chain in Neo4j
    Note over DB: u0 → u1 → u2 (each SUPERSEDES edge)

    Caller->>AE: findSupersessionChain(u2)
    AE->>DB: traverse SUPERSEDES edges
    DB-->>AE: [u0, u1, u2]
    AE-->>Caller: full lineage chain

    Caller->>AE: findPredecessor(u2)
    AE-->>Caller: Optional(u1)

    Caller->>AE: findSuccessor(u1)
    AE-->>Caller: Optional(u2)
```

### Supersession reasons

| Reason | Trigger |
|--------|---------|
| `CONFLICT_REPLACEMENT` | Conflict resolver chose REPLACE |
| `BUDGET_EVICTION` | Working-memory capacity exceeded |
| `DECAY_DEMOTION` | Activation score decayed below minimum |
| `USER_REVISION` | Operator explicitly revised via UI/tool |
| `MANUAL` | Programmatic supersession |

### Lineage queries

- `findSupersessionChain(unitId)` — returns the full predecessor → successor chain
- `findPredecessor(unitId)` — returns the immediate predecessor (if any)
- `findSuccessor(unitId)` — returns the immediate successor (if any)

Current limitation: lineage is 1:1 (no merge/split semantics). A unit has at most one predecessor and one successor.
