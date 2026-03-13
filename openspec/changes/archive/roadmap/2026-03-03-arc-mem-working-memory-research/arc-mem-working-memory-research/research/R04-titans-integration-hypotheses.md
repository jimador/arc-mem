# Research Task: TITANS-Inspired Integration Hypotheses for context units

## Task ID

`R04`

## Question

Given the validated overlap, what concrete and testable changes should be run in context units first?

## Strategy

Keep the current architecture boundary intact:

1. DICE remains extraction + revision.
2. Context Units remains trust-governed working memory.
3. Improvements target scoring/promotion/retention policy, not model-weight mutation.
4. Adversarial inputs are used as test stimuli to induce hallucination/contradiction and measure long-horizon relevance stability.

## Hypothesis Backlog (Prioritized)

### H1: Surprise-Weighted Promotion

Hypothesis:
1. Incorporating novelty/surprise into promotion decisions increases fact survival in long multi-turn conversations without increasing false promotions.

Proposed implementation:
1. Add `surpriseScore` to proposition evaluation context.
2. Blend with existing `trustScore` in `TrustPipeline`.
3. Route by policy bands (e.g., high trust + high surprise auto-promote, high trust + low surprise review).

Code touchpoints:
1. `src/main/java/dev/dunnam/arcmem/extract/UnitPromoter.java`
2. `src/main/java/dev/dunnam/arcmem/context unit/TrustPipeline.java`
3. `src/main/java/dev/dunnam/arcmem/context unit/TrustScore.java`

Metrics:
1. Fact Survival Rate
2. Context Unit-fact contradiction/hallucination incidence
3. Promotion precision/recall against assertion outcomes

Success criteria:
1. +5% relative improvement in fact survival with lower contradiction incidence vs baseline in long-horizon scenarios.
2. <= 2% increase in low-trust promotions.

### H2: Momentum-Like Reinforcement Carryover

Hypothesis:
1. Carrying reinforcement momentum for a short window after high-surprise events reduces post-event memory collapse.

Proposed implementation:
1. Track a short-lived reinforcement accumulator per context unit.
2. Decay accumulator each turn; consume into rank updates.

Code touchpoints:
1. `src/main/java/dev/dunnam/arcmem/context unit/ReinforcementPolicy.java`
2. `src/main/java/dev/dunnam/arcmem/context unit/DecayPolicy.java`
3. `src/main/java/dev/dunnam/arcmem/persistence/PropositionNode.java`

Metrics:
1. Mean Turns to First Drift
2. Recovery time after inconsistency events
3. Rank volatility across turns

Success criteria:
1. +10% Mean Turns to First Drift in long-horizon scenarios.
2. Lower rank volatility for high-value context units.

### H3: Adaptive Budget Under Memory Pressure

Hypothesis:
1. Pressure-aware budget/eviction policy outperforms fixed-cap policy under compaction and high-turn conditions.

Proposed implementation:
1. Introduce pressure index from token pressure + inconsistency frequency + compaction risk.
2. Modulate eviction aggressiveness and min-rank thresholds by pressure index.

Code touchpoints:
1. `src/main/java/dev/dunnam/arcmem/context unit/ArcMemEngine.java`
2. `src/main/java/dev/dunnam/arcmem/assembly/PromptBudgetEnforcer.java`
3. `src/main/java/dev/dunnam/arcmem/assembly/CompactionValidator.java`

Metrics:
1. Context Unit retention quality under token stress
2. Compaction integrity assertion pass rate
3. Runtime token budget violations

Success criteria:
1. +8% compaction-integrity pass rate on stress scenarios.
2. No increase in budget overflow events.

### H4: Chunk/Compaction Sensitivity Benchmark Track

Hypothesis:
1. Explicitly varying segment/compaction granularity will expose controllable degradation modes and inform safer defaults.

Proposed implementation:
1. Add scenario variants for low/medium/high compaction pressure.
2. Report new slice metrics in benchmark markdown export.

Code touchpoints:
1. `src/main/resources/simulations/`
2. `src/main/java/dev/dunnam/arcmem/sim/engine/SimulationTurnExecutor.java`
3. `src/main/java/dev/dunnam/arcmem/sim/report/MarkdownReportRenderer.java`

Metrics:
1. Fact survival by compaction setting
2. Inconsistency severity distribution by setting
3. Context Unit churn (created/evicted per turn)

Success criteria:
1. Stable degradation curve with no catastrophic drop between adjacent settings.
2. Clear "safe operating zone" documented for default config.

## Rollout Plan

1. Implement H4 first (instrumentation + benchmark visibility).
2. Implement H1 next (highest value, lowest architecture risk).
3. Add H2 (retention dynamics) once H1 baseline is measured.
4. Add H3 last (policy complexity + interaction risk).

## Risks

1. Surprise proxies may overfit to lexical novelty and miss semantic importance.
2. Momentum carryover may over-entrench early wrong context units.
3. Adaptive budget may add policy opacity and tuning burden.
4. More policy state may increase debugging complexity in simulation.

## Guardrails

1. Keep authority and trust gates mandatory before any promotion.
2. Keep CANON assignment human-gated.
3. Require ablation reports for each hypothesis before default enablement.
4. Feature-flag all new policies with deterministic fallback to current behavior.

## Recommendation

Proceed with a benchmark-first integration path (H4 -> H1 -> H2 -> H3). This sequence extracts TITANS-style memory dynamics where they are likely to help, while preserving the explicit governance model that defines context units.
