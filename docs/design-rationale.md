<!-- sync: openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/anchor-conflict -->
<!-- dice-integration-sync: openspec/specs/dice-integration-review-docs -->
<!-- last-synced: 2026-02-25 -->

# Design Rationale

## 1. The Problem

LLMs degrade in multi-turn conversations. Laban et al. (2025) measured across 200,000+ simulated conversations with 15 LLMs: a 39% average performance drop from single-turn to multi-turn settings, a 112% increase in unreliability (variance between best and worst runs), with degradation starting at 2+ turns regardless of information density. Models prematurely attempt solutions with incomplete information and then over-rely on those early attempts. Temperature reduction to 0.0 eliminates single-turn unreliability but 30%+ unreliability persists in multi-turn. The root cause is not context capacity but the absence of any mechanism to distinguish **load-bearing facts** from ambient context.

Standard mitigations fall short:

| Approach               | Why it fails                                                                             |
|------------------------|------------------------------------------------------------------------------------------|
| Longer context windows | More tokens does not mean better attention. Lost-in-the-middle effects persist.          |
| Summarization          | Lossy. Recursive summarization compounds errors.                                         |
| RAG                    | Retrieves relevant content but does not mandate consistency. No enforcement.             |
| Prompt engineering     | "Remember these facts" degrades as context grows. Vulnerable to confident contradiction. |

The problem is acute in agentic systems where invariants must hold across long horizons under adversarial pressure. A dead NPC must stay dead. A drug allergy must never be forgotten. A legal privilege must never be waived.

### Why D&D as a Test Domain

This project uses tabletop RPGs as its proving ground, but the approach is domain-agnostic. D&D is a particularly useful sandbox because:

- **Invariants are legible.** World facts, character states, and rules form a clear set of constraints. A dead NPC stays dead (barring necromancy shenanigans). Violations are immediately obvious.
- **Creative freedom is the point.** The value of the LLM is improvisation, elaboration, and responsive storytelling. Restricting this defeats the purpose.
- **Adversarial pressure is natural.** Players routinely test boundaries, make false claims, and try to manipulate the narrative — providing organic adversarial testing that mirrors real-world prompt injection concerns.
- **Long horizons are the norm.** Campaigns span dozens of sessions. Facts established early must persist across hundreds of turns.

This tension — *be creative, but don't break the rules* — exists wherever agentic systems operate within constraints.

## 2. Why Anchors

Anchors are **propositions promoted to load-bearing facts** with explicit lifecycle metadata. The core insight: some content must be *guaranteed present* in the LLM context, not merely retrievable. Anchors provide this guarantee through mandatory system-prompt injection combined with authority-governed lifecycle management.

Key design decisions and their rationale:

**Explicit state over implicit memory.** Facts are managed state with rank, authority, and provenance rather than raw conversation history the model must parse. This aligns with the RAG principle of combining parametric and external memory.

**Authority tiers as governance primitive.** A four-level hierarchy (`PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON`) creates policy hooks unavailable in flat retrieval stacks. The upgrade-only constraint prevents adversarial downgrade attacks.

**Hard budget enforcement.** A cap (default 20 active anchors) prevents context bloat. Long-context capability does not remove attention-allocation failures. A small, focused fact set outperforms a large dump.

**Adversarial resistance by design.** Anchors are formatted as authoritative instructions with explicit correction directives. The model is told to *actively resist* contradiction attempts, not merely recall facts.

**Mandatory injection over retrieval.** Unlike RAG (retrieved content competes for attention) or summarization (content may be dropped), anchors occupy a fixed system-prompt block. `AnchorsLlmReference` assembles the top-ranked anchors as an `ESTABLISHED FACTS` block injected before the user message.

**External validation** (from independent technical assessment):
- Explicit state beats implicit prompt memory — aligns with RAG framing of combining parametric and external memory.
- Authority tiers are the right governance primitive — creates policy hooks unavailable in plain retrieval stacks.
- Budgeted memory is realistic engineering — long-context capability does not remove attention-allocation failures.
- Adversarial simulation is a differentiator — most memory demos are descriptive; this repo includes adversarial scenarios and drift evaluation.

## 3. The Anchor Model

An anchor carries:

- **Rank** [100-900]: Importance score governing injection priority and eviction order. Clamped via `Anchor.clampRank()`. Reinforcement boosts (+50/confirmation), exponential decay reduces over time.
- **Authority**: Trust level. Upgrade-only: `PROVISIONAL(0) -> UNRELIABLE(1) -> RELIABLE(2) -> CANON(3)`. CANON is operator-designated only, never auto-assigned, never evicted, never decayed.
- **Pinned**: Immune to decay and eviction. Operator control.
- **Confidence**: Extraction confidence from DICE.
- **Reinforcement count**: Times re-confirmed. Drives authority upgrades.
- **Trust score**: Composite evaluation from multiple signals (source authority, extraction confidence, reinforcement history).

**Lifecycle flow:** DICE extraction -> duplicate detection -> conflict detection -> trust evaluation -> promotion decision (AUTO_PROMOTE / REVIEW / ARCHIVE) -> budget enforcement (evict lowest non-pinned if over cap) -> active anchor pool -> context assembly.

**Lifecycle diagram:**

```
DICE Extraction → Duplicate Detection → Conflict Detection
                                              │
                                    ┌─────────┴─────────┐
                                no conflict         conflict
                                    │                    │
                              Trust Evaluation    Authority-Based
                                    │              Resolution
                                    ▼
                           Promotion Decision
                        (AUTO_PROMOTE / REVIEW / ARCHIVE)
                                    │
                              Budget Enforcement
                          (evict lowest non-pinned)
                                    │
                             Active Anchor Pool
                     (reinforcement, decay, pin controls)
                                    │
                            Context Assembly
                    (top-ranked → ESTABLISHED FACTS block)
```

**Conflict resolution:** Authority-based. RELIABLE+ existing anchors are kept. High-confidence incoming propositions can replace lower-authority anchors. Otherwise, coexistence. LLM-based semantic comparison (`LlmConflictDetector`) with `NegationConflictDetector` as lexical fallback.

Implementation: `AnchorEngine` (lifecycle orchestration), `AnchorPromoter` (promotion pipeline), `AuthorityConflictResolver` (conflict resolution), `ExponentialDecayPolicy` (rank decay), `ThresholdReinforcementPolicy` (reinforcement), `AnchorsLlmReference` (context assembly), `PromptBudgetEnforcer` (token budgeting).

## 4. DICE Integration

This section addresses how Anchors composes with the DICE framework. Anchors is a downstream consumer of DICE, not a replacement. The integration strategy follows Option B: local implementation in `dice-anchors` with an upstream-friendly proposal for DICE extension points.

### 4.1 Concept Mapping: DICE Proposition Lifecycle -> Anchors Lifecycle

| DICE Concept            | Anchors Concept                | Relationship                                                                                              |
|-------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------|
| Proposition extraction  | Raw material                   | DICE extracts propositions from conversation text; Anchors selects a subset for promotion                 |
| Proposition revision    | Conflict/reinforcement trigger | DICE revisions feed Anchors conflict detection and reinforcement pipelines                                |
| Entity mentions         | Subject filtering              | DICE entities used by `SubjectFilter` to scope anchor queries                                             |
| Proposition persistence | Shared store                   | Both use `AnchorRepository` (Neo4j/Drivine). Anchors adds rank/authority/tier metadata                    |
| Incremental analysis    | Turn-by-turn extraction        | DICE `ConversationPropositionExtraction` runs per turn; Anchors processes results through promotion gates |

DICE owns extraction and revision. Anchors owns promotion, lifecycle governance, budget enforcement, and context injection.

### 4.2 Memory Layering: Separate and Complementary

DICE Agent Memory and Anchors serve **different retrieval purposes** and operate as separate layers:

| Layer                   | Mechanism                                          | Purpose                                                                                          |
|-------------------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------|
| **DICE Agent Memory**   | `searchByTopic`, `searchRecent`, `searchByType`    | Broad retrieval of relevant propositions, entity relationships, and historical context on demand |
| **Anchors working set** | Rank-sorted mandatory injection into system prompt | Guaranteed presence of load-bearing facts regardless of retrieval relevance scoring              |

Anchors **augments** DICE memory, it does not replace it. DICE retrieval provides the broader knowledge base. Anchors provides the invariant enforcement layer. A proposition may exist in DICE memory and never become an anchor. An anchor always has a corresponding DICE proposition as its origin.

### 4.3 Low-Trust Knowledge with Constrained Authority

Not all extracted knowledge deserves anchor status. The authority hierarchy allows low-trust knowledge to remain available without contaminating the invariant set:

- **PROVISIONAL** anchors carry provenance qualifiers in the injected context (tagged `[PROVISIONAL]`), signaling the model to treat them as tentative.
- Propositions below the AUTO_PROMOTE threshold (trust score < 0.80) enter the REVIEW queue or are archived, remaining in DICE's proposition store for retrieval without occupying anchor budget.
- Authority ceiling (persisted at promotion) constrains how high a low-provenance fact can be upgraded, preventing adversarial escalation through repetition alone.
- The `TrustSignal` composite evaluates source authority, extraction confidence, and reinforcement history to gate promotion decisions.

### 4.4 Runtime Boundaries

**What remains in DICE:**
- Proposition extraction from conversational text
- Entity mention and relationship identification
- Proposition revision and incremental analysis
- Base persistence schema and query contracts

**What this repo adds:**
- Rank, authority, and trust metadata on propositions
- Promotion pipeline with duplicate/conflict/trust gates (`AnchorPromoter`, `DuplicateDetector`, `LlmConflictDetector`)
- Budget enforcement and eviction policy (`AnchorEngine`, `PromptBudgetEnforcer`)
- Mandatory context injection (`AnchorsLlmReference`, `AnchorContextLock`)
- Decay and reinforcement policies (`ExponentialDecayPolicy`, `ThresholdReinforcementPolicy`)
- Adversarial simulation and drift evaluation harness (`sim/engine/`, `sim/report/`)

### 4.5 Extension Points for Future DICE Incorporation

| Extension Point                             | Purpose                                                             | Expected Contract                                                          | Current Limitation                                                                             |
|---------------------------------------------|---------------------------------------------------------------------|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `PropositionPipeline` post-extraction hooks | Tier candidate tagging after extraction                             | Callback receiving proposition + context, returning tier hint + confidence | No hook exists in DICE; `dice-anchors` processes extraction results after the fact             |
| `PropositionRepository` tier-aware queries  | Filter by memory tier (`COLD`, `WARM`, `HOT`) | Optional tier parameter on existing `findByContext` methods                | Current repo adds tier semantics at the application layer, not in shared persistence contracts |
| Incremental analysis context metadata       | Carry session/temporal metadata for validity decisions              | Session ID, timestamp, source provenance in analysis context               | DICE incremental analysis does not expose metadata needed for temporal validity                |
| Revision/conflict extension seam            | Temporal-aware and evidence-aware conflict classification           | Hook on `PropositionReviser` allowing external conflict policy             | Conflict detection is entirely application-side in `dice-anchors`; no DICE seam exists         |
| `MemoryTierClassifier` (proposed SPI)       | Classify propositions into tier candidates                          | Input: proposition + context; Output: tier + confidence + rationale        | Not yet proposed upstream; implemented locally as promotion logic                              |
| `MemoryTierPolicy` (proposed SPI)           | Apply transition rules between tiers                                | Input: proposition, tier, state; Output: transition decision               | Not yet proposed upstream                                                                      |
| `MemoryMutationAudit` (proposed SPI)        | Structured decision events for observability                        | Event payload for every state mutation                                     | Partially implemented via `anchor/event/` lifecycle events; not standardized                   |

All proposed interfaces default to no-op implementations so existing DICE adopters see no behavior change.

### 4.6 Known Integration Gaps and Risks

**Gaps:**
1. **No DICE lifecycle hooks.** All promotion/tiering logic runs after DICE extraction completes. Tighter integration requires upstream hook points that do not yet exist.
2. **No temporal validity primitives.** DICE propositions have no `validFrom`/`validTo` semantics. Anchors cannot represent "this was true then but not now" without application-level workarounds.
3. **No shared audit schema.** Anchor lifecycle events (`anchor/event/`) and DICE extraction events use different structures. Unified observability requires schema alignment.
4. **Text-keyed batch maps.** Trust pipeline and promotion paths key by proposition text rather than stable DICE proposition ID, risking collisions on normalization edge cases.
5. **Degraded parse handling still requires review workflows.** Duplicate/conflict parse failures now quarantine or degrade to review instead of auto-accept, but operator handling paths remain implementation-specific.

**Risks:**
1. **Semantic drift from DICE upstream.** If DICE evolves its own memory tiering, `dice-anchors` patterns may diverge. Option B mitigates this by maintaining adapter boundaries.
2. **Over-generalizing app-specific semantics.** The authority taxonomy (`PROVISIONAL/UNRELIABLE/RELIABLE/CANON`) is domain policy, not framework primitive. Upstreaming must keep governance policy outside DICE core.
3. **Temporal complexity.** Adding `validFrom`/`validTo` to propositions introduces state machine complexity that DICE may not want in its core.

### 4.7 Integration Summary

| Dimension       | Status                                                                                                                                                                                                    |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Intent**      | Demonstrate working-memory anchoring as a composable layer over DICE extraction, with evidence for upstream adoption                                                                                      |
| **Current fit** | DICE provides extraction; Anchors consumes output and adds lifecycle governance. Integration is functional but loosely coupled (no shared hooks)                                                          |
| **Known gaps**  | No DICE lifecycle hooks, no temporal primitives, text-keyed maps, fail-open parse paths                                                                                                                   |
| **Next steps**  | (1) propose `MemoryTierClassifier`/`MemoryTierPolicy`/`MemoryMutationAudit` SPIs to DICE upstream, (2) add temporal metadata to persistence model, (3) publish deterministic ablation manifests |

## 5. Positioning Against Related Work

| Feature                    | Anchors                                     | MemGPT                  | Graphiti/Zep              | HippoRAG             | ACON            |
|----------------------------|---------------------------------------------|-------------------------|---------------------------|----------------------|-----------------|
| Guaranteed prompt presence | Yes (mandatory injection)                   | Partial (memory blocks) | No (retrieved)            | No (retrieved)       | No (compressed) |
| Importance ranking         | Yes [100-900]                               | No                      | No                        | PageRank-based       | Task-aware      |
| Authority hierarchy        | Yes (4 levels, upgrade-only)                | No                      | No                        | No                   | No              |
| Budget enforcement         | Hard cap (20)                               | Token limit             | None                      | Top-k                | Token reduction |
| Conflict detection         | LLM-based semantic + lexical fallback       | No                      | Temporal                  | No                   | No              |
| Adversarial resistance     | Primary design goal                         | Not a focus             | Not a focus               | Not a focus          | Not a focus     |
| Temporal validity          | Not yet (planned)                           | No                      | Yes (bi-temporal)         | No                   | No              |
| Decay/reinforcement        | Exponential decay + threshold reinforcement | Self-edit               | Temporal validity windows | Spreading activation | Failure-driven  |
| Graph-native retrieval     | Neo4j store, no graph retrieval yet         | No                      | Yes                       | Yes                  | No              |

**Key differentiator:** Anchors is designed for adversarial resistance as a primary concern, not an afterthought. The closest comparison is MemGPT's fixed memory blocks; Anchors extends this with explicit ranking, authority governance, and lifecycle management. Graphiti/Zep's temporal model is the strongest complement and a planned integration direction.

## 6. Where It Might Not Work

Known limitations requiring further research:

- **Very long conversations** where the system prompt itself gets diluted and attention allocation fails even for injected content.
- **Sophisticated adversarial attacks** that don't directly contradict but subtly reframe facts over multiple turns.
- **Models with weak instruction following** that ignore system prompt directives regardless of formatting.
- **Cross-session persistence** where anchors must survive context resets and be reconstructed from storage.

## 7. Open Questions

1. **Dynamic budget and memory pressure** — Could anchors adapt budget dynamically based on conversation pressure? ACON's failure-driven compression suggests eviction policy could be driven by what causes drift, not just rank.
2. **Premature commitment and anchor timing** — Anchors established in early turns could compound premature commitment (per Laban et al.). When should anchors be promoted? Is there a minimum conversation maturity threshold?
3. **Bi-temporal validity** — Graphiti/Zep distinguishes world-time validity from system-recording time. Anchors currently have no temporal dimension. How should temporal validity interact with authority?
4. **Graph-based reinforcement** — HippoRAG uses spreading activation and personalized PageRank. Current reinforcement is count-based (+50 per mention). Could reinforcement consider knowledge graph position (centrality)?
5. **Self-editing anchors** — MemGPT allows model self-editing of memory blocks. Could the model propose modifications to anchors? Authority constraints could limit this to PROVISIONAL anchors.
6. **Operator-defined invariants** — In production, the most valuable anchors will be defined upfront by operators. This points toward an invariant definition API.
7. **Conflict detection at scale** — As the pool grows, conflicts may be indirect (two facts individually consistent but collectively contradictory). Graph-based consistency checking may be needed.

## 8. References

1. Laban, P. et al. (2025). *LLMs Get Lost In Multi-Turn Conversation*. [arXiv:2505.06120](https://arxiv.org/abs/2505.06120)
2. Packer, C. et al. (2023). *MemGPT: Towards LLMs as Operating Systems*. [arXiv:2310.08560](https://arxiv.org/abs/2310.08560)
3. Radhakrishnan, A. et al. (2025). *Graphiti: Building Real-Time Knowledge Graphs*. [arXiv:2501.13956](https://arxiv.org/abs/2501.13956)
4. Gutierrez, B.J. et al. (2024). *HippoRAG*. [arXiv:2405.14831](https://arxiv.org/abs/2405.14831) (NeurIPS 2024)
5. ACON Framework. *Task-Aware Compression*. [OpenReview](https://openreview.net/pdf?id=7JbSwX6bNL)
6. Johnson, R. (2026). *Agent Memory Is Not A Greenfield Problem*. [Embabel](https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561)
7. Maharana, A. et al. (2024). *LoCoMo: Evaluating Very Long-term Conversational Memory*. [arXiv:2402.17753](https://arxiv.org/abs/2402.17753)
8. Wu, Y. et al. (2023). *Recursive Summarization*. [arXiv:2308.15022](https://arxiv.org/abs/2308.15022)
