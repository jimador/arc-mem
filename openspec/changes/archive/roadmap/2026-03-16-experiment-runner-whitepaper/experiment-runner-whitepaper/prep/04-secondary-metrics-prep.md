# Prep: Secondary Metrics

## Feature

F04 — secondary-metrics

## Key Decisions

1. **Erosion rate**: Fraction of targeted facts whose activation score decreased after ≥2 attacks on the same target. Computed from per-fact attack history.
2. **Reactivation success**: Fraction of dormant units successfully reactivated. Only meaningful in scenarios with dormancy config.
3. **Compliance recovery**: Fraction of turns where compliance enforcement corrected a potential violation. Requires compliance enforcement to be enabled.
4. **Failure mode classification**: Extend drift evaluator prompt to return failure mode alongside verdict — single LLM call, not separate.
5. **Per-fact traces**: Stored in SimulationRunRecord, not as separate Neo4j nodes.

## Open Questions

1. Should erosion rate count activation score decrease or authority demotion (or both)?
2. What constitutes "targeted" — is it any attack turn, or only turns where the attack explicitly references the fact?
3. Should failure-mode classification be a separate enum or an extension of EvalVerdict?

## Acceptance Gate

- ScoringResult includes all new fields
- BenchmarkAggregator computes statistics for new metrics
- At least one scenario produces non-zero values for each new metric
- Markdown report includes new metrics section

## Research Dependencies

None — metrics are defined by whitepaper outline

## Handoff Notes

Start with per-fact traces (most data, enables the others). Then erosion rate (depends on traces). Then reactivation/compliance (narrower scope). Failure-mode classification is the riskiest (LLM prompt change) — do last.
