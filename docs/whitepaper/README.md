# ARC Whitepaper — Central Hub

All materials for the arXiv paper and accompanying technical blog post.

## Paper

**Working title**: *Activation-Ranked Context: Governed Working Memory for Long-Horizon LLM Conversations*

**Target venue**: arXiv (cs.AI / cs.CL)

**Status**: Experiment infrastructure complete. Validation run in progress. Full matrix pending.

## Documents

### Core

| Document | Path | Purpose |
|----------|------|---------|
| Whitepaper outline | [`docs/drafts/whitepaper-outline.md`](../drafts/whitepaper-outline.md) | Full paper structure with section content, hypotheses, methodology, figures/tables plan |
| Related work & research | [`docs/related-work-and-research.md`](../related-work-and-research.md) | Positioning vs MemGPT/Letta, Graphiti/Zep, RAG; ACT-R literature; research directions |
| Published blog post | [Medium](https://medium.com/@jamesdunnam/long-running-llm-conversations-need-working-memory-not-just-more-context-b929600a4e05) | Non-technical framing of the problem and ARC approach |
| Blog draft (original) | [`docs/drafts/blog.md`](../drafts/blog.md) | Source text for the published Medium article |

### Evaluation

| Document | Path | Purpose |
|----------|------|---------|
| Evaluation methodology | [`docs/evaluation.md`](../evaluation.md) | Conditions, scenarios, metrics, statistical methods, export formats |
| Experiment matrix config | [`arcmem-simulator/.../experiments/paper-matrix.yml`](../../arcmem-simulator/src/main/resources/experiments/paper-matrix.yml) | 7 conditions × 10 scenarios × 10 reps YAML definition |
| Validation config | [`arcmem-simulator/.../experiments/validation-run.yml`](../../arcmem-simulator/src/main/resources/experiments/validation-run.yml) | Quick 2-condition × 1-scenario sanity check |
| Roadmap | [`openspec/roadmaps/experiment-runner-whitepaper-roadmap.md`](../../openspec/roadmaps/experiment-runner-whitepaper-roadmap.md) | Feature sequencing and research tasks for experiment infrastructure |

### Architecture

| Document | Path | Purpose |
|----------|------|---------|
| Architecture overview | [`docs/architecture.md`](../architecture.md) | System architecture, module structure, data flows |
| Data flows | [`docs/data-flows.md`](../data-flows.md) | Turn execution pipeline, extraction, conflict resolution |
| Promotion & revision | [`docs/promotion-revision-supersession.md`](../promotion-revision-supersession.md) | AWMU lifecycle: promotion, revision, supersession |
| Attention tracker | [`docs/attention-tracker-architecture.md`](../attention-tracker-architecture.md) | Attention window and heat/pressure scoring |

### Project

| Document | Path | Purpose |
|----------|------|---------|
| CLAUDE.md | [`CLAUDE.md`](../../CLAUDE.md) | Project instructions, key files, design decisions |
| OpenSpec constitution | [`openspec/constitution.md`](../../openspec/constitution.md) | Governing architectural constraints |
| Project overview | [`openspec/project.md`](../../openspec/project.md) | Tech stack, completed initiatives, key subsystems |

## Hypotheses (from outline Section 7)

- **H1**: Full ARC improves fact survival and reduces contradictions vs no active-memory governance
- **H2**: Activation dynamics, trust gating, and authority controls each contribute independently
- **H3**: Hierarchical authority improves contradiction handling beyond flat authority
- **H4**: Compliance-gated high-authority units improve resistance to deception attacks
- **H5**: ARC produces better diagnostic visibility into failure modes than retrieval-only baselines

## Ablation Conditions

| Condition | What it disables | Hypotheses tested |
|-----------|-----------------|-------------------|
| FULL_AWMU | Nothing (control) | All |
| NO_AWMU | All injection + mutation | H1 |
| FLAT_AUTHORITY | Authority hierarchy | H3 |
| NO_RANK_DIFFERENTIATION | Rank dynamics | H2 |
| NO_TRUST | Trust pipeline | H2, H4 |
| NO_COMPLIANCE | Compliance enforcement | H4 |
| NO_LIFECYCLE | Decay, reinforcement, reactivation | H2 |

## Metrics

### Primary
- `factSurvivalRate` — % of ground truth facts never contradicted
- `contradictionCount` — total CONTRADICTED verdicts
- `majorContradictionCount` — MAJOR severity contradictions
- `driftAbsorptionRate` — % of engaged turns with zero contradictions
- `meanTurnsToFirstDrift` — average turn of first contradiction per fact

### Secondary
- `erosionRate` — % of repeatedly-attacked facts that eroded
- `unitAttributionCount` — facts with ≥1 matching injected unit
- `complianceRate` — % of turns that were constraint-respecting

### Statistical
- Cohen's d effect sizes between condition pairs
- Mann-Whitney U p-values (non-parametric)
- Benjamini-Hochberg FDR correction for multiple comparisons
- 95% confidence intervals per metric per cell

## Scenario Domains (27 total)

| Domain | Scenarios | Type |
|--------|-----------|------|
| D&D / fantasy | 19 | Scripted + adaptive adversarial, baseline, trust, compaction, dormancy |
| Operations / incident response | 2 | 1 scripted, 1 adaptive |
| Compliance / rule-bound | 2 | 1 scripted, 1 adaptive |
| Trust evaluation | 2 | Signal-specific |
| Multi-session | 1 | Persistence |
| Compaction | 1 | Context compression stress |

## Model Configuration

- **DM responses**: gpt-4.1-nano ($0.10/1M input, $0.40/1M output)
- **Drift evaluator**: gpt-4.1-mini ($0.40/1M input, $1.60/1M output)
- **Estimated cost**: ~$13–15 for full matrix (700 runs)

## Running Experiments

```bash
# Validation run (~$0.15)
./run-experiment.sh arcmem-simulator/src/main/resources/experiments/validation-run.yml

# Full matrix (~$13)
./run-experiment.sh arcmem-simulator/src/main/resources/experiments/paper-matrix.yml

# Results written to experiment-output/ with:
# - <name>.json    (full structured data)
# - <name>.csv     (flattened for pandas/R)
# - <name>.md      (human-readable report with significance annotations)
# - manifest.json  (reproducibility metadata)
```

## Reference Bibliography

See whitepaper outline Section 14 for the complete reference inventory, organized by:
- Core problem framing (Laban et al., Maharana et al.)
- Memory systems and retrieval (MemGPT, Letta, Graphiti, HippoRAG, A-MEM, Cognitive Workspace)
- ACT-R + LLM integration (4 papers positioned in Section 3.2)
- Security and compliance (PoisonedRAG, OWASP)
- Theory (ACT-R, AGM belief revision)

## Next Steps

1. Review validation run results
2. Run full matrix (~$13)
3. Analyze results — populate Tables 3, 5, 7 and Figures 7, 8 from the outline
4. Write technical blog post (deeper than Medium article, with methodology + results)
5. Write whitepaper prose from outline + data
6. Submit to arXiv
