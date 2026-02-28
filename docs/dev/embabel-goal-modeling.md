# Embabel Goal-Directed Orchestration Evaluation

**Status**: Evaluation Only (Feature F2)
**Embabel Agent Version**: 0.3.5-SNAPSHOT
**Completed**: 2026-02-28

This document evaluates whether Embabel's goal-directed orchestration patterns (typed action chaining, `@AchievesGoal` completion, GOAP planning) would improve the management of anchor lifecycle operations in dice-anchors. It analyzes the current imperative approach, maps potential goal-directed alternatives, assesses the core tension between Neo4j side-effects and Embabel's typed data-flow model, and provides a clear recommendation.

**Scope**: Trust pipeline orchestration, chatbot mode selection, blackboard state binding opportunity
**Non-Scope**: Implementation, refactoring, API coverage, DICE integration specifics

## Current Anchor Lifecycle (Imperative)

### Pipeline Overview

`AnchorPromoter` orchestrates proposition promotion through a sequential 5-gate pipeline. Each gate either filters candidates via boolean/zone checks or triggers Neo4j mutations via `AnchorEngine`.

**Location**: `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java`

### Gate Sequence with File:Line References

#### 1. Confidence Gate (Line 139–144)

**Purpose**: Fast filter of low-confidence propositions
**Behavior**: Drops propositions where `prop.getConfidence() < autoActivateThreshold`
**Implementation**: Simple numeric comparison, no I/O
**Mutation**: None
**Pass Rate Impact**: Early funnel checkpoint — discards ~30–50% of extraction output (config-dependent)

```java
// Line 139
if (prop.getConfidence() < threshold) {
    logger.debug("Skipping proposition {} - confidence {} below threshold {}",
            prop.getId(), prop.getConfidence(), threshold);
    continue;
}
postConfidence++;
```

#### 2. Dedup Gate (Line 146–150)

**Purpose**: Reject propositions semantically identical to existing anchors
**Behavior**: `DuplicateDetector.isDuplicate(prop.getText(), anchors)` combines:
  - Normalized string matching (fast, exact)
  - Optional LLM verification (semantic, expensive) — only if normalized match fails

In batch mode (`batchEvaluateAndPromote`, line 238–251): exact-text cross-reference runs first (no LLM), then intra-batch dedup, then batch LLM call.

**Mutation**: None
**Pass Rate Impact**: Typical 10–20% rejection rate for novel propositions

```java
// Lines 146–150
if (duplicateDetector.isDuplicate(prop.getText(), anchors)) {
    logger.info("Proposition {} is duplicate, filtering at dedup gate", prop.getId());
    continue;
}
postDedup++;
```

#### 3. Conflict Gate (Line 152–160)

**Purpose**: Detect semantic contradictions with existing anchors and resolve them
**Behavior**:
1. `engine.detectConflicts()` (line 152) — identifies semantic conflicts via LLM
2. `resolveConflicts()` (lines 352–429) — applies conflict resolution in two passes:
   - **Pass 1**: Compute all resolutions without side effects
   - **Pass 2**: Execute mutations only if proposition survives all checks

**Resolution Outcomes** (4 possible):
- `KEEP_EXISTING` — Reject incoming proposition (existing anchor wins)
- `REPLACE` — Archive existing anchor, accept incoming (supersede operation)
- `DEMOTE_EXISTING` — Demote existing anchor one authority level, accept incoming
- `COEXIST` — Allow both anchors

**Side Effects in Pass 2**:
- `REPLACE`: `engine.supersede()` (line 395)
- `DEMOTE_EXISTING`: `engine.demote()` (line 400) + `engine.reEvaluateTrust()` (line 401)
- `COEXIST`: `engine.reEvaluateTrust()` (line 406)
- `KEEP_EXISTING` (Pass 1 rejection): `engine.reEvaluateTrust()` for existing anchor (line 414)

**Mutation**: Yes (3–4 Neo4j updates possible per conflict)
**Pass Rate Impact**: Conflict-dependent; KEEP_EXISTING outcomes reject incoming proposition

```java
// Lines 152–160
var conflicts = engine.detectConflicts(contextId, prop.getText());
if (!conflicts.isEmpty()) {
    var resolutionResult = resolveConflicts(prop, conflicts);
    degradedConflictCount += resolutionResult.degradedConflictCount();
    if (!resolutionResult.accepted()) {
        continue;
    }
}
postConflict++;
```

#### 4. Trust Gate (Line 162–186)

**Purpose**: Evaluate proposition trust via multi-signal pipeline; filter low-trust candidates
**Behavior**:
1. Retrieve `PropositionNode` from repository (line 163)
2. Run `TrustPipeline.evaluate(node, contextId)` (line 168)
3. **Inspect promotion zone** — `TrustScore.promotionZone()` determines outcome:
   - `AUTO_PROMOTE` — Proceed to promote gate
   - `REVIEW` — Reject; requires human review (line 172–176)
   - `ARCHIVE` — Reject; proposition unworthy of anchor (line 177–181)

**Signals Evaluated by TrustPipeline**:
- Source authority (e.g., DM-extracted propositions boost trust)
- Extraction confidence (model confidence in the extraction)
- Reinforcement history (how many times proposition has been reinforced)

**TrustScore Result**:
- `score()` — Numeric trust value
- `promotionZone()` — Categorical zone (AUTO_PROMOTE, REVIEW, ARCHIVE)
- `authorityCeiling()` — Max authority level this proposition can reach (passed to promote gate)
- `signalAudit()` — Decision trail (for logging/debugging)

**Mutation**: None (read-only evaluation)
**Pass Rate Impact**: Typical 20–40% rejection rate for candidates in REVIEW/ARCHIVE zones

```java
// Lines 162–186
TrustScore trustScore = null;
var nodeOpt = repository.findPropositionNodeById(prop.getId());
if (nodeOpt.isPresent()) {
    var node = nodeOpt.get();
    logger.debug("Trust gate: proposition {} sourceIds={} confidence={}",
            prop.getId(), node.getSourceIds(), node.getConfidence());
    trustScore = trustPipeline.evaluate(node, contextId);
    logger.info("Trust gate: proposition {} score={} zone={} audit={}",
            prop.getId(), trustScore.score(), trustScore.promotionZone(),
            trustScore.signalAudit());
    if (trustScore.promotionZone() == PromotionZone.REVIEW) {
        logger.info("Proposition {} in REVIEW zone (score={}), skipping auto-promotion",
                prop.getId(), trustScore.score());
        continue;
    }
    if (trustScore.promotionZone() == PromotionZone.ARCHIVE) {
        logger.info("Proposition {} in ARCHIVE zone (score={}), skipping",
                prop.getId(), trustScore.score());
        continue;
    }
} else {
    logger.warn("Trust gate: proposition {} not found in repository, skipping trust evaluation",
            prop.getId());
}
postTrust++;
```

#### 5. Promote Gate (Line 188–194)

**Purpose**: Activate proposition as an anchor with rank and authority assignment
**Behavior**: Call `engine.promote()` with:
- `prop.getId()` — Proposition to promote
- `initialRank` — Config: anchor starting rank (clamped to [100, 900])
- `trustScore.authorityCeiling()` — Max authority from trust evaluation (optional)

**Engine Behavior**:
- **Budget Enforcement** — If anchors exceed configured budget (default 20), evicts lowest-ranked non-pinned anchors
- **Rank Assignment** — New anchor starts at `initialRank`, clamped via `Anchor.clampRank()`
- **Authority Ceiling** — If provided, anchor authority is capped at this level

**Mutation**: Yes (1 Neo4j create, 0–N evictions per promote call)
**Pass Rate Impact**: N/A (all surviving candidates are promoted unless budget forces evictions)

```java
// Lines 188–194
if (trustScore != null) {
    engine.promote(prop.getId(), initialRank, trustScore.authorityCeiling());
} else {
    engine.promote(prop.getId(), initialRank);
}
promoted++;
```

### Processing Modes: Sequential vs. Batch

**Sequential Mode** (`evaluateAndPromoteWithOutcome`, lines 120–200):
- Processes one proposition at a time through the full 5-gate sequence
- Each gate decision made independently
- Suitable for single-proposition extraction (e.g., targeted user inputs)

**Batch Mode** (`batchEvaluateAndPromoteWithOutcome`, lines 211–317):
- Processes all candidates together through Gates 1–4 using batch LLM calls (dedup, conflict, trust)
- Then promotes sequentially (Gate 5) to preserve per-promotion budget enforcement
- More efficient for high-volume extraction (e.g., simulation turns, conversation windows)
- Includes existing-anchor exact-text cross-reference before batch LLM calls (line 235–240)

Both produce equivalent results for a single proposition; batch processing optimizes for throughput with fewer LLM calls.

### Gate Interaction Model

**Sequential Dependency**: Gate N+1 depends on candidates surviving Gate N. Filtering at any gate removes proposition from subsequent gates.

**No Backtracking**: Once a proposition is rejected at a gate, it cannot re-enter at a later gate in the same pass. Rejected propositions are marked with event metadata for audit logging.

**Side-Effect Ordering**:
- Gates 1–2 (confidence, dedup): Read-only filtering
- Gate 3 (conflict): Conditional mutations (REPLACE, DEMOTE_EXISTING, COEXIST outcomes)
- Gate 4 (trust): Read-only evaluation
- Gate 5 (promote): Unconditional mutations (promote, evict)

**Order Invariant** (P3): Gates must execute in sequence: confidence → dedup → conflict → trust → promote. No reordering or skipping is permitted.

### Neo4j Mutation Points

All mutation calls delegate to `AnchorEngine`:

| Mutation | Method | Gate | Condition | Scope |
|----------|--------|------|-----------|-------|
| Archive existing anchor | `engine.supersede()` | Conflict (3) | REPLACE outcome | Supersedes anchor with new proposition |
| Demote existing anchor | `engine.demote()` | Conflict (3) | DEMOTE_EXISTING outcome | One authority level down |
| Re-evaluate trust | `engine.reEvaluateTrust()` | Conflict (3) | All outcomes that keep existing | Updates trust score, may trigger promotion/demotion |
| Promote proposition | `engine.promote()` | Promote (5) | All surviving candidates | Create anchor, possibly evict |

**Critical Invariant** (P2): Every conflict resolution outcome MUST trigger a corresponding mutation. Trust re-evaluation may cascade into promotions or demotions.

## Trust Pipeline Analysis (Goal-Directed Mapping)

### Proposed Typed Action Chain

If AnchorPromoter were refactored to use Embabel goal-directed orchestration, the 5-gate pipeline would map to typed `@Action` methods forming a data-flow chain:

```
PropositionCandidate
    ↓ [@Action validateConfidence]
ConfidenceResult (success/fail + reason)
    ↓ [@Action detectDuplicates]
DedupResult (duplicate status)
    ↓ [@Action detectAndResolveConflicts]
ConflictResult (KEEP/REPLACE/DEMOTE/COEXIST + mutations)
    ↓ [@Action evaluateTrust]
TrustResult (promotion zone + authority ceiling)
    ↓ [@Action promoteToAnchor] [@AchievesGoal]
PromotedAnchor (anchor record + mutation outcomes)
```

### Action Chaining Mechanism

**Type Matching**: Each `@Action` return type becomes available as an input parameter to subsequent actions. The Embabel GOAP planner resolves execution order automatically based on type availability.

**Example Pattern** (from Embabel `Stages.java`):
```java
@Action
Cook chooseCook(UserInput input) { /* returns Cook */ }

@Action
Order takeOrder(UserInput input) { /* returns Order */ }

@Action
@AchievesGoal(description = "Meal prepared with selected cook and order")
Meal prepareMeal(Cook cook, Order order, UserInput input) { /* returns Meal */ }
```

The planner recognizes that `prepareMeal` requires `Cook` and `Order` inputs, so it ensures `chooseCook` and `takeOrder` execute first.

### Proposed Action Signatures

**Gate 1: Confidence Validation**
```java
@Action
ConfidenceResult validateConfidence(PropositionCandidate candidate) {
    double threshold = properties.anchor().autoActivateThreshold();
    if (candidate.confidence() < threshold) {
        return ConfidenceResult.rejected(candidate.id(), threshold);
    }
    return ConfidenceResult.accepted(candidate);
}
```

**Gate 2: Duplicate Detection**
```java
@Action
DedupResult detectDuplicates(ConfidenceResult confidenceResult) {
    var anchors = engine.inject(contextId);
    if (duplicateDetector.isDuplicate(confidenceResult.text(), anchors)) {
        return DedupResult.duplicate(confidenceResult.text());
    }
    return DedupResult.unique(confidenceResult);
}
```

**Gate 3: Conflict Detection & Resolution**
```java
@Action
ConflictResult detectAndResolveConflicts(DedupResult dedupResult) {
    var conflicts = engine.detectConflicts(contextId, dedupResult.text());
    // Compute resolutions without mutations (Pass 1)
    // Return typed result object with resolution outcomes
    return new ConflictResult(dedupResult, conflicts, resolutions);
}
```

**Gate 4: Trust Evaluation**
```java
@Action
TrustResult evaluateTrust(ConflictResult conflictResult) {
    var node = repository.findPropositionNodeById(conflictResult.propositionId()).orElse(null);
    var trustScore = trustPipeline.evaluate(node, contextId);
    if (trustScore.promotionZone() != PromotionZone.AUTO_PROMOTE) {
        return TrustResult.filtered(trustScore.promotionZone());
    }
    return TrustResult.approved(trustScore);
}
```

**Gate 5: Promotion (Terminal Action)**
```java
@Action
@AchievesGoal(description = "Proposition promoted to anchor with trust verification and conflict resolution applied")
PromotedAnchor promoteToAnchor(TrustResult trustResult, ConflictResult conflictResult) {
    // Apply all pending mutations from conflict resolution (Pass 2)
    var mutations = applyConflictMutations(conflictResult);

    // Promote proposition to anchor
    engine.promote(trustResult.propositionId(), initialRank, trustResult.authorityCeiling());

    return new PromotedAnchor(trustResult.propositionId(), mutations);
}
```

### Type-Matching Execution Order

The Embabel planner automatically determines execution order:

1. **`promoteToAnchor`** requires `TrustResult` and `ConflictResult` inputs
2. **`evaluateTrust`** requires `ConflictResult` → forces (3) to execute first
3. **`detectAndResolveConflicts`** requires `DedupResult` → forces (2) to execute first
4. **`detectDuplicates`** requires `ConfidenceResult` → forces (1) to execute first
5. **`validateConfidence`** requires raw `PropositionCandidate` → entry point

**Result**: Planner infers execution order as 1 → 2 → 3 → 4 → 5, matching the current sequential pipeline.

### Cost Annotation Not Applicable

The `@Action(cost)` parameter influences the planner's path selection when multiple routes exist to the goal. Since gate order is fixed and no alternative paths are available, cost has no effect on AnchorPromoter orchestration.

**Implication**: Goal-directed orchestration adds no planning value here — it's used for **observability and structure**, not for **adaptive decision-making**.

### Reference: Embabel Chaining Example

Embabel documentation (R01-embabel-api-surface.md) provides the `Stages.java` pattern as the canonical example of typed action chaining. The pattern shows:
- Multiple independent `@Action` methods returning distinct types
- A terminal `@Action` annotated with `@AchievesGoal`
- Type matching resolving execution order
- No explicit orchestration code required

The proposed AnchorPromoter refactoring follows this pattern exactly, with the addition of passing intermediate result objects through the chain to maintain data-flow purity (key to addressing the side-effect tension).

## Side-Effect Handling Analysis

### Core Tension: Immutable Data Flow vs. Neo4j Mutations

Embabel's goal-directed model assumes actions operate on immutable typed data:

```
Input1 + Input2 → [Action] → Output
```

Each action processes inputs and returns a typed output. The framework tracks data availability via type matching. This model works well for stateless computation.

**The Problem**: AnchorPromoter operations are not stateless. Each gate has side effects:

- **Conflict gate** may demote, archive, or supersede existing anchors
- **Trust re-evaluation** (triggered by conflict resolution) may promote or demote
- **Promote gate** may evict lowest-ranked anchors to enforce budget

These are **Neo4j mutations** that change persistent state. Embabel has no built-in model for tracking side-effect ordering or constraints (e.g., "demote existing before promoting incoming").

### Three Approaches to Side-Effect Handling

#### Option A: Impure Actions (Mutations Inside @Action)

**Approach**: Apply Neo4j mutations directly inside each `@Action` method.

```java
@Action
ConflictResult detectAndResolveConflicts(DedupResult dedupResult) {
    var conflicts = engine.detectConflicts(contextId, dedupResult.text());
    for (var conflict : conflicts) {
        var resolution = engine.resolveConflict(conflict);
        switch (resolution) {
            case REPLACE -> engine.supersede(...);  // MUTATION
            case DEMOTE_EXISTING -> {
                engine.demote(...);  // MUTATION
                engine.reEvaluateTrust(...);  // MUTATION
            }
            // ...
        }
    }
    return new ConflictResult(dedupResult, conflicts, resolutions);
}
```

**Benefits**:
- Minimal refactoring — gates execute as-is with @Action annotations added
- No intermediate result objects needed (current boolean/zone logic can stay)
- Familiar pattern — looks like current AnchorPromoter code

**Drawbacks**:
- **Breaks data-flow model** — Actions are no longer pure functions; hidden side effects are invisible to the planner
- **Error handling incomplete** — If a mutation fails mid-action, the planner doesn't know; subsequent actions may proceed with stale data
- **Testing complexity** — Must mock `AnchorEngine` for every action; integration tests required to verify mutation ordering
- **Observability lost** — Embabel instrumentation doesn't track side effects; only typed outputs are visible

**Refactoring Scope**: **LOW** (annotations + optional logging enhancements)

#### Option B: Functional Descriptors (Command Pattern)

**Approach**: Actions return **side-effect descriptors** (commands) instead of mutating directly. A final terminal action applies all mutations in sequence.

```java
record DemoteCommand(String anchorId, DemotionReason reason) {}
record ReevaluateCommand(String anchorId) {}
record PromoteCommand(String propositionId, int rank, Authority ceiling) {}

@Action
ConflictResult detectAndResolveConflicts(DedupResult dedupResult) {
    var conflicts = engine.detectConflicts(contextId, dedupResult.text());
    var commands = new ArrayList<Object>();  // Track mutations
    for (var conflict : conflicts) {
        var resolution = engine.resolveConflict(conflict);
        switch (resolution) {
            case REPLACE -> commands.add(new SupersedeCommand(...));
            case DEMOTE_EXISTING -> {
                commands.add(new DemoteCommand(...));
                commands.add(new ReevaluateCommand(...));
            }
            // ...
        }
    }
    return new ConflictResult(dedupResult, conflicts, resolutions, commands);  // No mutations yet
}

@Action
@AchievesGoal(...)
PromotedAnchor promoteToAnchor(TrustResult trust, ConflictResult conflict) {
    // Pass 1: Collect all commands from conflict result
    var allCommands = conflict.commands();
    allCommands.add(new PromoteCommand(...));  // Promote is the final command

    // Pass 2: Execute commands in order (safe atomic semantics possible)
    for (var command : allCommands) {
        executeCommand(command);
    }
    return new PromotedAnchor(...);
}
```

**Benefits**:
- **Pure data flow** — All actions return typed results; no hidden mutations
- **Composable** — Commands can be collected, inspected, logged, re-ordered, or replayed
- **Error recovery** — Terminal action can validate all commands before executing; partial failures are detectable
- **Testable** — Unit test each action in isolation (returns typed result); integration test only terminal action
- **Embabel friendly** — Data flow is fully typed and observable; planner sees all outputs

**Drawbacks**:
- **HIGH refactoring scope** — Introduces command pattern indirection; must refactor all mutation points
- **Command class explosion** — Requires a command class per mutation type (10+ commands for full pipeline)
- **Error handling complexity** — Terminal action must implement atomic execution or rollback semantics
- **Learning curve** — Team unfamiliar with command pattern; adds cognitive overhead

**Refactoring Scope**: **HIGH** (new command hierarchy + terminal executor + error handling strategy)

#### Option C: Hybrid (Result Objects + Terminal Executor)

**Approach**: Gates return typed **result objects** (no mutations). A single terminal action applies all mutations in a controlled sequence.

```java
record ConflictOutcome(
    ConflictDetector.Conflict conflict,
    ConflictResolver.Resolution resolution
) {}

@Action
ConflictResult detectAndResolveConflicts(DedupResult dedupResult) {
    var conflicts = engine.detectConflicts(contextId, dedupResult.text());
    var outcomes = new ArrayList<ConflictOutcome>();
    for (var conflict : conflicts) {
        var resolution = engine.resolveConflict(conflict);  // Decision, no mutation
        outcomes.add(new ConflictOutcome(conflict, resolution));
    }
    return new ConflictResult(dedupResult, outcomes);  // Result object, no mutations
}

@Action
@AchievesGoal(...)
PromotedAnchor promoteToAnchor(
    TrustResult trustResult,
    ConflictResult conflictResult  // Carries mutation outcomes without executing them
) {
    // Pass 1: Execute conflict mutations (from result object)
    for (var outcome : conflictResult.outcomes()) {
        switch (outcome.resolution()) {
            case REPLACE -> engine.supersede(...);
            case DEMOTE_EXISTING -> {
                engine.demote(...);
                engine.reEvaluateTrust(...);
            }
            // ...
        }
    }

    // Pass 2: Promote
    engine.promote(trustResult.propositionId(), initialRank, trustResult.authorityCeiling());

    return new PromotedAnchor(...);
}
```

**Benefits**:
- **Clean separation** — Gates compute decisions; terminal action executes mutations
- **Moderate refactoring** — No command classes; current decision logic stays, mutations move to terminal
- **Error handling** — Terminal action owns mutation sequencing; can implement atomic semantics or rollback
- **Observability** — Result objects are typed and visible; Embabel can track them
- **Testability** — Test decision logic independently; test mutation orchestration in terminal action

**Drawbacks**:
- **Mutation batching complexity** — Terminal action must manage conflict mutations + promote together; error at promote could leave conflict mutations incomplete
- **Partial failure handling** — If promote fails after conflict mutations, no easy rollback (Neo4j transactions would be needed)
- **Result object design** — Must ensure result objects capture enough state to execute mutations later (information loss risk)

**Refactoring Scope**: **MEDIUM** (introduce result objects, move conflict mutation logic to terminal action, add error handling)

### Side-Effect Handling Recommendation

**For AnchorPromoter**: Option C (Hybrid) balances observability gains with reasonable refactoring scope.

**Rationale**:
- Option A (impure) loses the primary benefit of goal-directed patterns (observability)
- Option B (functional) is overkill for a deterministic 5-gate pipeline with fixed order
- Option C preserves typed data flow while keeping mutation logic localized to the terminal action

**Implementation Strategy** (if adopted):
1. Introduce result record types (`ConfidenceResult`, `DedupResult`, etc.)
2. Refactor gates to return result objects (no mutations yet)
3. Move conflict mutation logic into the terminal `@AchievesGoal` action
4. Wrap mutations in a transaction-like scope (Neo4j driver handles atomicity)

## Comparison Table

| Aspect | Current (Imperative) | Goal-Directed | Winner | Reasoning |
|--------|----------------------|--------------|--------|-----------|
| **Observability** | Gate calls are opaque; only high-level funnel counts logged | Each action observable via Embabel instrumentation; typed outputs visible | Goal-Directed | Embabel tracks action execution and data flow; easier to trace decision points |
| **Testability** | Integration-heavy; must mock entire `AnchorEngine` + repository; gate order tested implicitly | Unit-friendly; test each action independently with typed inputs/outputs; integration test only terminal action | Goal-Directed | Typed boundaries enable isolated testing; less mocking required |
| **Error Handling** | Boolean pass/fail per gate; failures logged but not structured; no partial failure info | Structured result types with decision context; terminal action can validate all outcomes before executing | Goal-Directed | Result objects carry failure reason; enables better error reporting and recovery |
| **Code Clarity** | Straightforward sequential if-statements; easy to follow top-to-bottom | Multi-action abstraction layer; requires understanding type-matching chaining pattern; less linear | Imperative | Current code is immediately readable; goal-directed adds cognitive overhead unless team is familiar with patterns |
| **Refactoring Scope** | None | MEDIUM (Option C: result objects + terminal action) to HIGH (Option B: command pattern) | Imperative | Current code requires no changes; goal-directed requires substantial refactoring |
| **Type Safety** | Loose (boolean returns, zone checks via if-statements; easy to miss a case) | Strict (typed action chaining; type system enforces data flow; harder to accidentally skip a gate) | Goal-Directed | Type system prevents accidental gate reordering or skipping; exhaustive type matching in compiler |

### Summary by Dimension

- **Observability**: Goal-directed wins — Embabel instrumentation + typed outputs = better visibility
- **Testability**: Goal-directed wins — Typed boundaries enable unit testing without mocks
- **Error Handling**: Goal-directed wins — Structured result types provide context
- **Code Clarity**: Imperative wins — Sequential if-statements are simpler than type-matching chains
- **Refactoring Scope**: Imperative wins — No changes required; goal-directed requires medium-to-high effort
- **Type Safety**: Goal-directed wins — Type system enforces pipeline structure

**Trade-off Summary**: Goal-directed approach trades moderate refactoring effort for measurable gains in observability, testability, and error handling. The gains are real but incremental — not game-changing for a deterministic 5-gate pipeline.

## Chatbot Mode Assessment

### Current Mode: Utility

**Configuration** (ChatConfiguration.java:27):
```java
@Bean
Chatbot chatbot(AgentPlatform agentPlatform) {
    return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
}
```

**Characteristics**:
- Single `@Action` per user message (ChatActions.respond)
- No orchestration — action is triggered directly by UserMessage
- LLM generates tool calls; tools are invoked; results fed back to LLM
- Conversation-driven multi-turn interaction
- No explicit goal completion tracking

**Current Usage in dice-anchors**:
- `ChatActions.respond()` is invoked for each user message (line 68, ChatActions.java)
- Action loads active anchors, renders template, invokes LLM with tools, broadcasts event for async extraction
- Result: Single response per user input; no multi-action coordination

### Alternative Mode: GOAP (Goal-Oriented Action Planning)

**Constructor** (from R01 research):
```java
new AgentProcessChatbot(agentPlatform, agentSource, conversationFactory)
```

**Characteristics**:
- Multi-action orchestration per user message
- GOAP planner selects action sequence to achieve a goal
- `@AchievesGoal` annotation marks goal completion point
- `@Action(cost)` parameter influences path selection
- Planner chooses lowest-cost route if alternatives exist

**Use Cases for GOAP**:
- Multi-step user requests requiring sequential action coordination
- Fallback paths when primary action fails or times out
- Complex decision trees where planner chooses optimal path
- Goals that require parallel exploration

### Fixed Gate Order Implication

**Question**: Does the trust pipeline benefit from GOAP planning when gate order is predetermined?

**Answer**: No.

**Rationale**: GOAP planning adds value when the planner can choose between alternative action paths based on cost. In AnchorPromoter:

1. **Gate order is deterministic** — Gates must execute in fixed sequence (confidence → dedup → conflict → trust → promote)
2. **No alternatives exist** — Each gate has exactly one path (pass or fail); no branching logic to optimize
3. **Cost parameter unused** — Since execution order is fully determined by type dependencies, the `@Action(cost)` parameter has no effect
4. **Result**: GOAP planner has no decisions to make; it simply enforces type-matching order

**Analogy**: Using GOAP planning for a deterministic pipeline is like using A* search when the path is already defined. The planner is idle.

**Conclusion**: Utility mode (single action per message) is appropriate for dice-anchors' current chatbot pattern. There is no multi-action orchestration need in the trust pipeline, so GOAP mode adds complexity without benefit.

### Recommendation

**Keep utility mode**. The chat interface is single-action-per-message by design. If future chat enhancements require multi-action orchestration (e.g., "retrieve context, ask clarifying question, synthesize answer"), revisit GOAP mode then.

## Blackboard State Opportunity

### Current State Management

**Context ID Extraction** (ChatActions.java:174–177):
```java
private String resolveContextId(ActionContext context) {
    var processContextId = context.getProcessContext().getProcessOptions().getContextIdString();
    return processContextId != null ? processContextId : FALLBACK_CONTEXT;
}
```

State (context ID and related objects) is passed through method parameters and action context, then extracted at runtime.

**Similar Pattern in Simulation** (not shown here, but documented in DICE integration):
- `SimulationRunContext` is passed to services
- Extracted context ID via property access
- No persistent binding across turns

### Blackboard Alternative

Embabel's **blackboard state binding** system allows persistent type-matched object injection across action boundaries:

```java
// In initialization
agentProcess.bindProtected(CONTEXT_KEY, currentContext);
agentProcess.addObject(simulationEngine);
agentProcess.addObject(anchorRepository);

// In actions, dependencies resolved by type matching
@Action
void respondWithContext(Conversation conversation, AnchorEngine engine, AnchorRepository repo) {
    // engine and repo are automatically injected from blackboard
}
```

**Benefits**:
- Type-safe dependency injection — no string-based context ID extraction
- Persistent state across action boundaries — reduces parameter passing
- Cleaner action signatures — dependencies are declared, not hidden in ActionContext

### Potential Typed Bindings for dice-anchors

| Binding | Type | Current Extraction | Blackboard Alternative |
|---------|------|---------------------|----------------------|
| Context ID | String | `resolveContextId(ActionContext)` | `bindProtected("context", contextId)` + inject as String parameter |
| Anchor Engine | Reference | Injected via ChatActions record | `agentProcess.addObject(anchorEngine)` + auto-inject by type |
| Trust Pipeline | Reference | Injected via ChatActions record | `agentProcess.addObject(trustPipeline)` + auto-inject by type |
| Simulation Run Context | Object | Passed via parameter chain | `bindProtected("simContext", runContext)` + auto-inject |
| Conversation | Reference | Already available via Embabel | Auto-injected (no binding needed) |

### Status: DEFERRED to Future Change

**Rationale for Deferral**:

1. **Requires multi-action refactoring** — Blackboard bindings are most valuable when multiple actions consume the same state. Current dice-anchors uses a single `ChatActions.respond()` action; multi-action refactoring (triggered by goal-directed adoption) would create opportunities for blackboard use.

2. **Incremental benefit** — Current parameter injection is explicit and clear. Blackboard reduces boilerplate but doesn't unlock new capabilities in single-action mode.

3. **Risk vs. Benefit** — Adopting blackboard bindings now without multi-action patterns would add complexity without measurable benefit. Better to defer until goal-directed patterns are adopted.

4. **Implementation dependency** — If goal-directed adoption (Option C: Hybrid side-effect handling) is deferred, blackboard adoption should also defer. They're complementary investments.

**Conditions for Reconsideration**:
- Multi-action patterns become standard in dice-anchors (e.g., chat orchestration, simulation workflow)
- Blackboard bindings demonstrably reduce boilerplate in multi-action scenarios
- Team familiarity with Embabel patterns increases

**Future Work Tracking**: If goal-directed orchestration is adopted (Option C recommended), add a follow-up task to evaluate blackboard state bindings in the context of the refactored multi-action pipeline.

## Recommendation

**DEFER goal-directed orchestration of AnchorPromoter at this time.**

### Rationale

1. **Incremental observability gains vs. moderate refactoring cost**
   - Goal-directed approach would improve observability, testability, and error handling
   - Comparison table shows goal-directed wins on 4 of 6 dimensions (observability, testability, error handling, type safety)
   - However, current imperative pipeline is straightforward and well-logged; observability gaps are not acute
   - Refactoring cost (MEDIUM: Option C) is not justified by incremental gains alone

2. **Fixed gate order eliminates GOAP planning value**
   - The primary value of goal-directed patterns is adaptive orchestration (planner chooses paths)
   - AnchorPromoter gates are deterministically ordered; planner has no decisions to make
   - Type-matching would enforce order, but we already have explicit control flow that's easier to reason about

3. **Chat interface is single-action-per-message**
   - Chatbot mode (utility) is appropriate; no multi-action orchestration needed
   - GOAP mode would add complexity without benefit

4. **Blackboard state bindings depend on multi-action adoption**
   - Current single-action design doesn't benefit from blackboard; deferral is consistent

5. **Embabel API stability**
   - Embabel 0.3.5-SNAPSHOT is in active development
   - Waiting for a stable release (0.4.0 or later) reduces risk of patterns becoming outdated
   - Deferring allows time for team familiarity to grow via other features (F1: API inventory, F3: DICE integration)

### Decision Outcome

**Do not refactor AnchorPromoter to use goal-directed orchestration at this time. Continue with imperative 5-gate pipeline.**

### Conditions for Re-Evaluation

Revisit this decision if **any** of the following occur:

1. **Observability becomes critical** — E.g., production trace logs show gate decision opacity is hindering debugging
2. **Embabel releases stable GOAP/planning examples** — If 0.4.0+ provides production-hardened patterns for side-effect handling, re-evaluate the functional/hybrid options
3. **Multi-action chat orchestration is needed** — E.g., user requests require sequential tool use (retrieve, clarify, synthesize), making GOAP planning valuable
4. **Blackboard adoption becomes standard** — If other features adopt blackboard bindings successfully, the benefits may justify revisiting AnchorPromoter

### What This Means for Implementation

- **No code changes to AnchorPromoter or ChatActions**
- **Keep current utility-mode chatbot**
- **Do not implement goal-directed action chaining** (avoided refactoring effort)
- **Do not implement blackboard state bindings** in this change
- **Reference this evaluation** in code review if future goal-directed patterns are proposed for other components

### Future Feature Interactions

This decision does **not** preclude goal-directed patterns in other areas:

- **DICE proposition extraction** (F3) could use `@AchievesGoal` patterns if extraction flow benefits from orchestration
- **Simulation turn execution** could adopt goal-directed orchestration if multi-stage turn logic (setup, execute, evaluate, report) becomes complex
- **Chat workflow extensions** could use GOAP planning if multi-turn user requests require adaptive action selection

Each component should be evaluated independently.

## References

- **Embabel Agent Documentation**: https://docs.embabel.com/embabel-agent/api-docs/0.3.5-SNAPSHOT/index.html
- **Embabel Coding Style & Patterns**: https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/.embabel/coding-style.md
- **R01: Embabel API Surface Research**: `openspec/roadmaps/embabel-dice-integration/research/R01-embabel-api-surface.md`
- **AnchorPromoter Source**: `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java`
- **ChatActions Source**: `src/main/java/dev/dunnam/diceanchors/chat/ChatActions.java`
- **ChatConfiguration Source**: `src/main/java/dev/dunnam/diceanchors/chat/ChatConfiguration.java`
- **Feature Specification**: `openspec/changes/embabel-goal-modeling/specs/embabel-goal-modeling/spec.md`
- **Design Document**: `openspec/changes/embabel-goal-modeling/design.md`
- **Task Checklist**: `openspec/changes/embabel-goal-modeling/tasks.md`
- **DICE Integration Reference**: `docs/dev/dice-integration.md`

---

**Document Created**: 2026-02-28
**Status**: Evaluation Complete
**Action Required**: None (evaluation only — no implementation)
