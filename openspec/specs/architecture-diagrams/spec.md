# architecture-diagrams Specification

## Purpose
TBD - created by archiving change arcmem-architecture-docs. Update Purpose after archive.
## Requirements
### Requirement: Memory unit lifecycle state machine diagram
`docs/architecture.md` SHALL include a Mermaid `stateDiagram-v2` showing the complete memory unit lifecycle: extraction → PROVISIONAL → UNRELIABLE → RELIABLE → CANON, with demotion paths, archival, and supersession transitions. The diagram MUST annotate invariants A3a (no auto-CANON), A3b (CANON immune to auto-demotion), and A3d (pinned immunity).

#### Scenario: Developer traces authority transitions
- **WHEN** a developer reads the lifecycle state machine in `docs/architecture.md`
- **THEN** they can identify every valid authority transition, which transitions are automatic vs HITL-gated, and which invariants protect each state

#### Scenario: CANON demotion path visible
- **WHEN** a developer looks for how CANON units can be demoted
- **THEN** the diagram shows CANON → RELIABLE requires explicit HITL decanonization via `CanonizationGate`

### Requirement: Conflict detection pipeline diagram
`docs/architecture.md` SHALL include a Mermaid flowchart showing the conflict detection pipeline: `CompositeConflictDetector` dispatching to `LlmConflictDetector`, `NegationConflictDetector`, and `PrologConflictDetector` based on `ConflictDetectionStrategy`; `ConflictIndex` precomputed lookup with fallback; and `AuthorityConflictResolver` mapping conflicts to resolutions (KEEP_EXISTING, REPLACE, COEXIST, DEMOTE_EXISTING).

#### Scenario: Developer identifies conflict detection path
- **WHEN** a developer reads the conflict detection diagram
- **THEN** they can trace from incoming text through strategy selection, detection, index lookup, and resolution to the final action taken on conflicting units

#### Scenario: Fallback path visible
- **WHEN** the `ConflictIndex` has no precomputed entry for a unit pair
- **THEN** the diagram shows fallback to live `LlmConflictDetector` evaluation

### Requirement: Trust pipeline scoring diagram
`docs/architecture.md` SHALL include a Mermaid flowchart showing the trust evaluation pipeline: `TrustPipeline` → `TrustEvaluator` → individual signals (`ImportanceSignal`, `NoveltySignal`, `GraphConsistencySignal`) → weighted aggregation → `TrustScore` with promotion zone (IMMEDIATE/CONDITIONAL/HOLD/REJECT) and authority ceiling. The diagram MUST show `DomainProfile` modifier influence.

#### Scenario: Developer traces trust evaluation
- **WHEN** a developer reads the trust pipeline diagram
- **THEN** they can identify which signals contribute to the trust score, how they are weighted, and how the promotion zone constrains authority promotion

### Requirement: Maintenance strategy state machines
`docs/architecture.md` SHALL include Mermaid `stateDiagram-v2` diagrams for each maintenance mode: REACTIVE (per-turn decay/reinforcement only), PROACTIVE (pressure-triggered 5-step sweep), and HYBRID (reactive + proactive). The PROACTIVE diagram MUST show the `MemoryPressureGauge` composite score dimensions (budget, conflict rate, decay rate, compaction rate) and thresholds (light-sweep 0.4, full-sweep 0.8).

#### Scenario: Developer understands sweep trigger
- **WHEN** a developer reads the PROACTIVE maintenance diagram
- **THEN** they can identify the four pressure dimensions, the composite score formula, and the threshold that triggers audit → refresh → consolidate → prune → validate

#### Scenario: Developer compares strategies
- **WHEN** a developer reads all three maintenance diagrams side by side
- **THEN** they can distinguish REACTIVE (inline only), PROACTIVE (sweep only), and HYBRID (both) behavior

### Requirement: Context assembly pipeline diagram
`docs/architecture.md` SHALL include a Mermaid sequence diagram showing context assembly: `ArcMemLlmReference.getContent()` → unit loading → authority grouping → adaptive footprint templates → token budgeting via `PromptBudgetEnforcer` → `BudgetStrategy` eviction → formatted context block. The diagram MUST distinguish the three retrieval modes (BULK, HYBRID, TOOL).

#### Scenario: Developer traces prompt construction
- **WHEN** a developer reads the context assembly diagram
- **THEN** they can trace from `getContent()` call through unit selection, budget enforcement, authority-graduated formatting, and final prompt injection

#### Scenario: Retrieval modes distinguishable
- **WHEN** a developer compares retrieval modes in the diagram
- **THEN** they can identify BULK (all active within budget), HYBRID (relevance-scored top-k + CANON always included), and TOOL (empty, on-demand via tool calls)

### Requirement: End-to-end conversation data flow
A new file `docs/data-flows.md` SHALL contain a Mermaid sequence diagram tracing the complete path: user message → `ChatActions` → DICE extraction → `DuplicateDetector` → `ConflictDetector` → `UnitPromoter` → `ArcMemEngine.promote()` → `TrustPipeline` → Neo4j persist → next turn assembly via `ArcMemLlmReference` → `ComplianceEnforcer` → LLM response → user.

#### Scenario: Developer traces message-to-context path
- **WHEN** a developer reads the end-to-end sequence diagram
- **THEN** they can identify every service involved from user input to LLM output, including extraction, deduplication, conflict detection, promotion, trust evaluation, persistence, assembly, and compliance enforcement

#### Scenario: Compliance enforcement position clear
- **WHEN** a developer reads the data flow
- **THEN** they can see that compliance enforcement occurs after LLM generation, validating the response against active memory units

### Requirement: Promotion and demotion example flows
`docs/data-flows.md` SHALL include at least five concrete scenario traces with Mermaid sequence diagrams:
(A) New fact: extraction → PROVISIONAL → 3 reinforcements → UNRELIABLE → 4 more → RELIABLE.
(B) Conflict resolution: incoming fact conflicts with existing → `AuthorityConflictResolver` → REPLACE → predecessor archived with successorId.
(C) Decay demotion: rank decays over turns → drops below threshold → auto-demotion RELIABLE → UNRELIABLE.
(D) Canonization: operator action → `CanonizationGate` HITL approval → RELIABLE → CANON.
(E) Supersession: `supersede(predecessorId, successorId, reason)` → predecessor archived → successor linked → lineage chain queryable via `findSupersessionChain()`.

#### Scenario: Promotion trace verifiable
- **WHEN** a developer reads scenario A
- **THEN** they can count the reinforcement thresholds (3 for UNRELIABLE, 7 for RELIABLE) and verify they match `ReinforcementPolicy` defaults

#### Scenario: Supersession lineage traceable
- **WHEN** a developer reads scenario E
- **THEN** they can trace the predecessor → successor chain and understand how `findSupersessionChain()` reconstructs the full lineage

#### Scenario: CANON promotion shows HITL gate
- **WHEN** a developer reads scenario D
- **THEN** they can see that CANON promotion requires explicit `CanonizationGate` approval and is never automatic (invariant A3a)

