## 1. Architecture Diagrams in `docs/architecture.md`

- [x] 1.1 Add memory unit lifecycle state machine diagram (stateDiagram-v2): extraction → PROVISIONAL → UNRELIABLE → RELIABLE → CANON with demotion paths, archival, supersession, and invariant annotations (A3a, A3b, A3d) — _Spec: Memory unit lifecycle state machine diagram_
- [x] 1.2 Add conflict detection pipeline flowchart: CompositeConflictDetector → strategy dispatch → LlmConflictDetector / NegationConflictDetector / PrologConflictDetector → ConflictIndex precomputed lookup with fallback → AuthorityConflictResolver → resolution outcomes — _Spec: Conflict detection pipeline diagram_
- [x] 1.3 Add trust pipeline scoring flowchart: TrustPipeline → TrustEvaluator → signals (ImportanceSignal, NoveltySignal, GraphConsistencySignal) → weighted aggregation → TrustScore (score, promotionZone, authorityCeiling) with DomainProfile modifiers — _Spec: Trust pipeline scoring diagram_
- [x] 1.4 Add maintenance strategy state machines (stateDiagram-v2): REACTIVE (per-turn only), PROACTIVE (pressure-triggered 5-step sweep with MemoryPressureGauge dimensions and thresholds 0.4/0.8), HYBRID (both) — _Spec: Maintenance strategy state machines_
- [x] 1.5 Add context assembly pipeline sequence diagram: ArcMemLlmReference.getContent() → unit loading → authority grouping → adaptive footprint → PromptBudgetEnforcer → BudgetStrategy eviction → formatted block; distinguish BULK / HYBRID / TOOL retrieval modes — _Spec: Context assembly pipeline diagram_
- [x] 1.6 Verify all diagrams render correctly in GitHub-flavored markdown

## 2. End-to-End Data Flows in `docs/data-flows.md`

- [x] 2.1 Create `docs/data-flows.md` with introduction explaining the document's purpose
- [x] 2.2 Add end-to-end conversation data flow sequence diagram: user message → ChatActions → DICE extraction → DuplicateDetector → ConflictDetector → UnitPromoter → ArcMemEngine.promote() → TrustPipeline → Neo4j → next turn ArcMemLlmReference → ComplianceEnforcer → LLM → user — _Spec: End-to-end conversation data flow_
- [x] 2.3 Add scenario A: promotion trace — new fact extraction → PROVISIONAL → 3 reinforcements → UNRELIABLE → 4 more → RELIABLE — _Spec: Promotion and demotion example flows_
- [x] 2.4 Add scenario B: conflict resolution — incoming fact conflicts → AuthorityConflictResolver → REPLACE → predecessor archived with successorId — _Spec: Promotion and demotion example flows_
- [x] 2.5 Add scenario C: decay demotion — rank decays over turns → threshold breach → auto-demotion RELIABLE → UNRELIABLE — _Spec: Promotion and demotion example flows_
- [x] 2.6 Add scenario D: canonization — operator action → CanonizationGate HITL → RELIABLE → CANON — _Spec: Promotion and demotion example flows_
- [x] 2.7 Add scenario E: supersession — supersede(predecessorId, successorId, reason) → predecessor archived → successor linked → lineage chain via findSupersessionChain() — _Spec: Promotion and demotion example flows_

## 3. Existing Doc Updates

- [x] 3.1 Update `docs/promotion-revision-supersession.md` to cross-reference new diagrams in `architecture.md` and `data-flows.md`
- [x] 3.2 Verify all invariant annotations (A1–A4) in diagrams match constitution Article V

## 4. Verification

- [x] 4.1 Verify all Mermaid diagrams render in GitHub markdown preview (no syntax errors)
- [x] 4.2 Cross-check diagram class/method names against actual source code for accuracy
- [x] 4.3 Verify docs/data-flows.md is linked from docs/README.md or architecture.md
