# Collaborative Context Unit Mutation Roadmap

## Intent

Enable legitimate revisions of established context units in multi-actor collaborative contexts without compromising long-horizon consistency and hallucination/contradiction control. The end-state is: (1) a conflict classification pipeline that distinguishes revision from contradiction, (2) prompt compliance language that permits authorized revisions while maintaining consistency controls, (3) cascade logic for dependent context units during supersession, (4) provenance metadata for audit and heuristics, and (5) optional UI controls for explicit context unit mutation. This roadmap is industry-generic — the problem applies anywhere context units are collaboratively authored (healthcare, legal, supply chain), not only to D&D.

## RFC 2119 Compliance

Normative roadmap statements SHOULD use RFC 2119 keywords only (`MUST`, `SHOULD`, `MAY`, and their negations).

## Research Configuration

- `Research mode`: `scoped`
- `Research criteria summary`: Prefer codebase evidence first, then AI memory framework comparisons, then cross-domain prior art from established disciplines (belief revision, TMS, MVCC, supply chain, medical informatics, legal, collaborative knowledge management). Web sources for both AI framework docs and foundational academic literature.
- `Evidence freshness expectation`: AI framework docs SHOULD be within 18 months (2024-2026). Foundational cross-domain literature has no freshness limit — classic references (AGM 1985, TMS 1979, MVCC) remain authoritative.
- `Minimum sources per research task`: 3 (with at least 1 codebase evidence source for AI tasks; at least 4 distinct domains for cross-domain survey).

## Scope

### In Scope

1. Conflict type classification (REVISION vs CONTRADICTION vs WORLD_PROGRESSION) in the existing conflict detection pipeline.
2. Prompt compliance template modifications to support revision-eligible context units.
3. Dependent context unit cascade logic during supersession events.
4. Lightweight source provenance metadata on context units (extraction turn, speaker role).
5. Optional UI controls for explicit context unit mutation via the Context Units panel.
6. Research backlog for unresolved design decisions (intent detection accuracy, cascade strategy, mixed-authority revision handling).

### Out of Scope

1. Code implementation (deferred to OpenSpec changes).
2. Changes to core context unit invariants (rank bounds, authority upgrade-only, CANON never auto-assigned).
3. Multi-agent (A2A) revision governance (future work, captured in research roadmap Track C).
4. Ownership-based access control (this roadmap avoids gating on "who owns the context unit" — revision intent is the signal, not ownership).
5. Changes to the simulation harness or benchmarking framework.

## Constraints

1. Conform to `openspec/constitution.md` — Neo4j-only persistence, constructor injection, records for DTOs, context unit invariants.
2. Build on existing conflict detection infrastructure (F02 conflict-detection-calibration-core, delivered) and supersession plumbing (F04 bi-temporal-validity-and-supersession, archived).
3. Extension points MUST be additive — no breaking changes to `ConflictDetector`, `ConflictResolver`, `ArcMemEngine`, or `UnitPromoter` interfaces.
4. The ARC-Mem framework's consistency controls MUST NOT be weakened by revision support — false-positive revision classification (adversarial input misclassified as revision) is a critical risk.
5. CANON authority context units MUST NOT be revision-eligible via automated classification — CANON mutations require explicit operator action through the `CanonizationGate`.

## Proposal Waves

| Wave | Feature ID | Feature Slug | Priority | Depends On | Visibility | OpenSpec Change Slug | Spec Coverage |
|------|------------|--------------|----------|------------|------------|----------------------|---|
| 1 | F01 | revision-intent-classification | MUST | F02 (conflict-detection-calibration-core, done) | UI + Observability | revision-intent-classification | — No spec yet |
| 1 | F02 | prompt-compliance-revision-carveout | MUST | F01 | Observability | prompt-compliance-revision-carveout | — No spec yet |
| 2 | F03 | dependent-unit-cascade | SHOULD | F01, F04 (bi-temporal-validity-and-supersession, done) | UI + Observability | dependent-unit-cascade | — No spec yet |
| 2 | F04 | unit-provenance-metadata | SHOULD | none | Observability | unit-provenance-metadata | — No spec yet |
| 3 | F05 | ui-controlled-mutation | MAY | F01, F03 | UI | ui-controlled-mutation | — No spec yet |

## Research Backlog

| Task ID | Question | Target Feature(s) | Channels | Timebox | Success Criteria | Output Doc |
|---------|----------|-------------------|----------|---------|------------------|------------|
| R00 | What is the observed failure mode when a collaborator attempts to revise an established context unit via free-form chat? | All | codebase | completed | Failure mode documented with transcript, context unit state progression, and root cause analysis. | `openspec/roadmaps/collaborative-unit-mutation/research/R00-chat-mutation-failure-analysis.md` |
| R01 | How reliably can an LLM classify revision intent vs adversarial contradiction, and what prompt strategies minimize false positives? | F01 | codebase + web | completed | Prompt strategy defined; false-positive rate target established; evaluation methodology documented. | `openspec/roadmaps/collaborative-unit-mutation/research/R01-revision-intent-classification-accuracy.md` |
| R02 | Which cascade strategy (temporal co-creation, semantic dependency, explicit DERIVED_FROM edges) best identifies dangling propositions without over-invalidation? | F03 | codebase + web + repo-docs | completed | Strategy comparison matrix; recommended approach with tradeoff analysis; prototype validation criteria. | `openspec/roadmaps/collaborative-unit-mutation/research/R02-dependent-unit-cascade-strategy.md` |
| R03 | How should the compliance prompt template handle mixed-authority revision scenarios (e.g., RELIABLE context unit revised by PROVISIONAL evidence)? | F02 | codebase + repo-docs | completed | Authority-level revision rules defined; prompt template draft with compliance language; edge cases documented. | `openspec/roadmaps/collaborative-unit-mutation/research/R03-mixed-authority-revision-compliance.md` |
| R04 | How do existing AI memory frameworks (Letta/MemGPT, Zep/Graphiti, LangMem, Mem0, A-MemGuard, MemOS) handle fact mutation, and do any distinguish update from contradiction? | F01, F02, F03 | web + similar-repos + repo-docs | completed | Comparison matrix across 3+ frameworks; transferable patterns identified; gaps in existing approaches documented. | `openspec/roadmaps/collaborative-unit-mutation/research/R04-ai-memory-framework-comparison.md` |
| R05 | What established solutions exist in non-AI domains (TMS, AGM belief revision, MVCC, CRDTs, ECO/BOM, medical records, legal precedent, Wikipedia edit classification, accounting) for maintaining authoritative state while allowing legitimate revisions? | All | web + repo-docs | completed | Pattern catalog from 4+ distinct domains; transferability assessment per feature (F01-F05); recurring cross-domain patterns identified. | `openspec/roadmaps/collaborative-unit-mutation/research/R05-cross-domain-prior-art.md` |

## Sequencing Rationale

1. **Research first** — R04 (AI framework comparison) and R05 (cross-domain prior art) SHOULD be completed before Wave 1 proposals begin. These surveys may reshape F01-F03 scope by revealing proven patterns (e.g., TMS justification-based retraction for cascade, AGM entrenchment ordering for authority-gated revision, Wikipedia edit classifier features for intent detection). R01-R03 are feature-specific research that can run in parallel with or after R04/R05.
2. **Wave 1** establishes the core classification capability. Without distinguishing revision from contradiction, no downstream feature can safely permit context unit mutation. F01 extends the conflict detection pipeline; F02 updates the prompt compliance template to act on the classification. These are tightly coupled and MUST ship together.
3. **Wave 2** builds on classification to handle the consequences of revision. F03 solves the "dangling proposition" problem — when a parent context unit is revised, dependent context units need cascade invalidation. F04 adds provenance metadata that improves cascade heuristics and audit. These are independently proposable but complementary.
4. **Wave 3** provides an explicit UI escape hatch. F05 is a MAY-priority convenience that makes revision intent unambiguous (clicking "Edit" vs typing in chat). It depends on F01 and F03 to ensure the underlying mutation and cascade logic exists.

## Feature Documents

1. `openspec/roadmaps/collaborative-unit-mutation/features/01-revision-intent-classification.md`
2. `openspec/roadmaps/collaborative-unit-mutation/features/02-prompt-compliance-revision-carveout.md`
3. `openspec/roadmaps/collaborative-unit-mutation/features/03-dependent-unit-cascade.md`
4. `openspec/roadmaps/collaborative-unit-mutation/features/04-unit-provenance-metadata.md`
5. `openspec/roadmaps/collaborative-unit-mutation/features/05-ui-controlled-mutation.md`

## Research Documents

1. `openspec/roadmaps/collaborative-unit-mutation/research/R00-chat-mutation-failure-analysis.md` (completed)
2. `openspec/roadmaps/collaborative-unit-mutation/research/R01-revision-intent-classification-accuracy.md`
3. `openspec/roadmaps/collaborative-unit-mutation/research/R02-dependent-unit-cascade-strategy.md`
4. `openspec/roadmaps/collaborative-unit-mutation/research/R03-mixed-authority-revision-compliance.md`
5. `openspec/roadmaps/collaborative-unit-mutation/research/R04-ai-memory-framework-comparison.md`
6. `openspec/roadmaps/collaborative-unit-mutation/research/R05-cross-domain-prior-art.md`

## Research Findings Summary

Research tasks R03, R04, and R05 are complete. R01 and R02 are in progress. Key findings that reshape feature scope:

### R04 — AI Memory Framework Comparison (completed)

No surveyed AI memory framework (Letta/MemGPT, Zep/Graphiti, LangMem, Mem0, A-MemGuard, MemOS) distinguishes update from contradiction. F01's revision-vs-contradiction classification is **genuinely novel**. Graphiti's temporal supersession is the closest structural analog but lacks intent classification. A-MemGuard validates graduated authority but uses binary accept/reject gating.

### R05 — Cross-Domain Prior Art (completed)

19 transferable patterns cataloged from 9 non-AI domains. Three highest-impact findings:

1. **F01 SHOULD use a two-axis classification model** (intent × impact) inspired by Wikipedia's ORES, not a flat 3-way enum. Decomposing classification into two independent axes (does the speaker intend to revise? × is the revision consistent with remaining context units?) produces a 2×2 matrix that handles edge cases (good-faith-but-damaging edits) the flat model misses.
2. **F03 cascade SHOULD use JTMS-style label propagation** (TMS, 40+ years of implementation history) combined with ECO interchangeability evaluation. JTMS provides the algorithm; interchangeability prevents over-invalidation.
3. **F02 authority gating SHOULD incorporate materiality** (from accounting IAS 8) — a function of authority AND impact radius — not just authority level alone. A PROVISIONAL context unit with 10 dependents is more "material" than a RELIABLE context unit with no dependents.

See `R05-cross-domain-prior-art.md` Pattern Catalog (P01–P19) for implementation hints per feature.

### R01 — Revision Intent Classification Accuracy (completed)

Extend the existing `conflict-detection.jinja` prompt (not a separate pipeline stage) with a `conflictType` field. The `Conflict` record gets a `ConflictType` enum (additive, backward-compatible). Key findings:

1. **Prompt strategy**: Two-step model (detect conflict → classify type) using chain-of-thought via a `"reasoning"` JSON field before `"conflictType"`. The existing `drift-evaluation-system.jinja` already demonstrates reliable 3-way classification — same pattern applies.
2. **False-positive target**: < 5% achievable with few-shot prompted GPT-4.1/Claude Sonnet. Authority gating (RELIABLE not revision-eligible by default; CANON never) eliminates high-stakes false-positive scenarios independently.
3. **Context signals**: Linguistic revision markers ("actually", "I meant"), context unit authority level, and untrusted-data framing. Full conversation context excluded from F01 scope.
4. **Adversarial mitigation**: Conservative default (ambiguous = CONTRADICTION); separate confidence threshold for REVISION classification.

### R02 — Dependent Context Unit Cascade Strategy (completed)

**Recommended: two-phase cascade** — temporal co-creation as fast filter (zero LLM cost) + LLM semantic dependency as precision filter.

1. **Phase 1**: Find active context units within the same extraction timestamp window as the superseded context unit. No new graph edges required — uses existing `PropositionNode.created` timestamps.
2. **Phase 2**: For each candidate, LLM evaluation: "given revision from A to B, does this context unit remain true, become false, or is it independent?" Only "becomes false" candidates invalidated.
3. **Authority-gated cascade**: Hard cascade for PROVISIONAL/UNRELIABLE; soft cascade (flag for review) for RELIABLE; CANON exempt.
4. **`DERIVED_FROM` edges deferred** to future iteration — would replace Phase 1 with precise graph traversal.
5. **Gap**: No per-turn batch ID persisted on `PropositionNode` — temporal co-creation uses timestamp proximity.

### R03 — Mixed-Authority Revision Compliance (completed)

PROVISIONAL and UNRELIABLE context units are revision-eligible by default. RELIABLE is configurable via `context unit.revision.reliable-revisable` (default: false). CANON is never revisable. Draft Jinja2 template adds `[revisable]` annotation with ~155 additional tokens for typical 20-unit budget. Includes updated Critical Instructions and Verification Protocol language.

## Global Risks

1. **False-positive revision classification** — An adversarial prompt mimicking revision language ("Actually, the king was never real") could bypass consistency controls. Mitigation: conservative classification threshold; CANON context units exempt; human-in-the-loop for RELIABLE+ revisions.
2. **Over-cascade during supersession** — Cascade logic may invalidate context units that are only loosely related to the revised context unit. Mitigation: R02 research task to evaluate strategies; configurable cascade depth limit.
3. **Prompt template complexity** — Adding revision-eligible annotations to the compliance template increases prompt token cost and DM instruction complexity. Mitigation: keep revision carveout minimal; use tiered compliance (only annotate revision-eligible context units, not all).
4. **Cross-domain generalization** — Strategies validated in D&D may not transfer to healthcare/legal domains where revision semantics differ. Mitigation: R05 surveys 9 non-AI domains for transferable patterns; keep the classification vocabulary generic (REVISION/CONTRADICTION/PROGRESSION); domain-specific tuning deferred to operator invariants.
5. **Reinventing solved problems** — This problem has been studied under different names in multiple mature disciplines (TMS, AGM belief revision, ECO processes, medical record amendment). Mitigation: R04 and R05 research tasks SHOULD be completed before F01 proposal to ensure we build on established patterns rather than from first principles.

## Exit Criteria

Roadmap is complete when:

1. Every feature has a doc with proposal seed content.
2. Dependencies and sequencing are explicit.
3. Each feature has measurable acceptance criteria.
4. Each feature has at least one visibility channel (UI or Observability).
5. Research tasks R01–R05 have output doc stubs. *(done — all 6 stubs created)*
6. Cross-domain prior art survey (R05) has produced a pattern catalog with transferability assessments. *(done — 19 patterns cataloged)*
7. AI memory framework comparison (R04) has produced a comparison matrix. *(done — 6 frameworks compared)*
8. Each feature is ready for `/opsx:new <change-slug>`.

## Suggested Proposal Commands

1. `/opsx:new revision-intent-classification`
2. `/opsx:new prompt-compliance-revision-carveout`
3. `/opsx:new dependent-unit-cascade`
4. `/opsx:new unit-provenance-metadata`
5. `/opsx:new ui-controlled-mutation`

## Known Limitations

1. **Revision classification is LLM-dependent** — accuracy will vary across models. Cross-model generalization is a follow-up concern, not addressed in this roadmap.
2. **No multi-agent revision governance** — this roadmap covers single-context revision. A2A scenarios (one agent revising another agent's context units) are captured in the research roadmap Track C.
3. **CANON context units remain immutable** — by design, CANON context units cannot be revision-eligible. This is an intentional constraint, not a limitation, but operators seeking CANON revision MUST use the CanonizationGate.
