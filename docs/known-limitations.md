# Known Limitations

This document is an honest accounting of gaps, known issues, and areas needing further work. dice-anchors is an early-stage exploration — several components work well enough to demonstrate the approach but are not production-ready.

## Trust Evaluation

### Profile Tuning

The three predefined domain profiles (BALANCED, SECURE, NARRATIVE) have reasonable-looking thresholds but have not been empirically tuned against a ground truth dataset. The threshold values were chosen by inspection, not optimization.

## Compaction

### Summary Quality

`CompactionValidator` detects when protected facts are lost during LLM-generated compaction, but does not automatically retry or regenerate. The quality of summaries depends on the model and prompt. The validator catches losses; recovery is left to the operator.

### Token Estimation

Token counts are estimated at ~4 characters per token. This is a rough heuristic that varies by model, language, and content type. Actual token counts could differ significantly.

## Simulation Harness

### Single-Threaded UI

The simulation harness runs one scenario at a time. There is no support for parallel scenario execution or batch runs from the UI. This is acceptable for the current exploration phase.

### No Statistical Analysis

Individual runs are not statistically meaningful — LLM outputs are non-deterministic. Drawing conclusions requires multiple runs of the same scenario, which the harness supports (via Run History and comparison) but doesn't automate.

### Auto-Generated Adversarial Messages

When scripted turns are exhausted, `generateAdversarialMessage()` picks a random ground truth fact and random attack strategy. The quality of auto-generated attacks is inconsistent and may not match the sophistication of hand-crafted scripted turns.

## Persistence

### Neo4j Dependency

The application requires a running Neo4j instance. There is no embedded or in-memory database option for quick local testing without Docker.

### No Cross-Session Persistence

Anchors are scoped to a single session (chat or simulation run). There is no mechanism for persisting anchors across application restarts in the chat flow, though simulation records are stored via the `RunHistoryStore` interface.

## Cross-Model Generalization

Testing has been primarily with OpenAI models (gpt-4.1-mini). The anchor injection approach — formatting facts in the system prompt with authoritative framing — relies on the model's instruction-following behavior. Models with weaker instruction following may be less responsive to the "MUST NOT contradict" directives.

## Open Research Questions

These are questions we don't have answers to yet:

1. **Optimal budget size:** The default of 20 active anchors was chosen by inspection. How does performance vary with different budgets? At what point does adding more anchors dilute the effectiveness of each individual anchor?

2. **Decay calibration:** The exponential decay half-life is configurable but has not been empirically tuned. What half-life provides the best balance between fact persistence and context freshness?

3. **Authority upgrade thresholds:** Are 3 and 7 reinforcements the right thresholds for UNRELIABLE and RELIABLE upgrades? These values were chosen by inspection.

4. **Adversarial robustness under pressure:** The current scenarios test direct contradiction and reframing. More sophisticated attacks (multi-turn coordination, gradual semantic drift, exploitation of model-specific behaviors) have not been tested.

5. **Diminishing returns:** At what conversation length does system prompt injection become insufficient? Very long conversations may dilute system prompt attention regardless of how authoritatively the facts are framed.
