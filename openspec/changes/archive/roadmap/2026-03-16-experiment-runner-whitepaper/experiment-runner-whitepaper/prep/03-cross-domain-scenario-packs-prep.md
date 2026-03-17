# Prep: Cross-Domain Scenario Packs

## Feature

F03 — cross-domain-scenario-packs

## Key Decisions

1. **Two domains**: Operations/incident response + Compliance/rule-bound. Healthcare and legal deferred (domain expertise needed).
2. **Reuse existing schema**: No changes to SimulationScenario record or ScenarioLoader. New YAML files only.
3. **Attack strategies**: Reuse existing strategy catalog. Map D&D attack patterns to domain-equivalent prompts in scenario YAML.
4. **Persona adaptation**: Replace DM/player framing with assistant/user. Persona config in YAML handles this.
5. **Authority mapping**: Policy facts start as RELIABLE or CANON. Incident state facts start as PROVISIONAL.

## Open Questions

1. Should scenarios use the "DM" role label or introduce domain-appropriate role labels (e.g., "assistant", "system")?
2. How many scenarios per domain is minimum viable for cross-domain claims? (Likely 2-3 deterministic + 1 adaptive)
3. Do we need to extend the strategy catalog with domain-specific strategies, or are existing ones sufficient with different prompt templates?

## Acceptance Gate

- 2 domain packs, ≥2 scenarios each
- At least 1 deterministic scenario per domain
- All scenarios load and execute without errors
- Ground truth facts produce meaningful verdicts (not degenerate)

## Research Dependencies

R03 (Cross-domain scenarios) — scenario structure designs should be validated before implementation

## Handoff Notes

Start with operations domain — easier to design ground truth facts (system states are binary: read-only/writable, deployed/rolled-back). Compliance domain second — policy rules are also fairly binary (allowed/not-allowed given conditions).
