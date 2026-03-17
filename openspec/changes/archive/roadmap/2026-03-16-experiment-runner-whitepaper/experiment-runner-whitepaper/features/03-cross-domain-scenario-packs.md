# Feature: Cross-Domain Scenario Packs

## Feature ID

`F03`

## Summary

Create scenario packs outside the D&D/narrative domain to support cross-domain generalization claims in the whitepaper. The paper identifies 4 candidate domains (operations, support, healthcare, legal/compliance). This feature delivers at least 2 domain packs with deterministic scripted scenarios suitable for claim-facing results, plus optional adaptive scenarios for stress-facing analysis.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: All 23 existing scenarios are D&D/fantasy domain. The whitepaper's Section 7.2 calls for cross-domain evidence, and Section 11.4 explicitly flags "domain skew and external validity limits" as a limitation. Without non-D&D scenarios, the paper can only claim results for narrative/tabletop use cases.
2. Value delivered: Cross-domain evidence enabling broader generalization claims (even if hedged). At minimum, the paper can say "results replicate across N domains" rather than "results are specific to tabletop narrative."
3. Why now: Scenarios are independent of other features and can be developed in parallel with F02 and F04.

## Scope

### In Scope

1. Operations/incident response scenario pack (2-3 scenarios)
2. Compliance/rule-bound assistant scenario pack (2-3 scenarios)
3. Each pack MUST include at least 1 deterministic scripted scenario (for claim-facing results)
4. Each pack SHOULD include at least 1 adaptive scenario (for stress-facing analysis)
5. Ground truth facts, seed units, and assertions per scenario
6. Scenario category taxonomy extension (new categories beyond adversarial/baseline/trust/etc.)

### Out of Scope

1. Healthcare domain (requires domain expertise for realistic ground truth — defer to future work)
2. Legal domain (similar expertise constraint)
3. Scenario auto-generation tooling
4. Changes to SimulationTurnExecutor or scenario loading infrastructure (those should work as-is)

## Dependencies

1. Feature dependencies: none (scenarios are YAML files consumable by existing infrastructure)
2. Technical prerequisites: Understanding of scenario YAML schema, attack strategy catalog, assertion types
3. Parent objectives: Cross-domain validation for whitepaper Section 9

## Research Requirements

1. Open questions: What ground-truth patterns work for ops/compliance domains? What attack strategies map to non-narrative contexts?
2. Required channels: web, repo-docs
3. Research completion gate: At least 2 domain scenario structures sketched with ground-truth facts and attack mapping

## Impacted Areas

1. Packages/components: `simulations/` YAML directory, potentially `ScenarioLoader` if new categories need registration
2. Data/persistence: New scenario IDs in Neo4j run records
3. Domain-specific subsystem impacts: Attack strategies need domain-appropriate prompt templates (e.g., "the server was never in read-only mode" instead of "the guardian was never hostile")

## Visibility Requirements

### UI Visibility

1. User-facing surface: New scenarios appear in ExperimentConfigPanel scenario selector
2. What is shown: Scenario title, category, domain
3. Success signal: User can select ops and compliance scenarios and run experiments against them

### Observability Visibility

1. Logs/events/metrics: Run logs include scenario domain/category
2. Trace/audit payload: SimulationRunRecord includes scenario metadata
3. How to verify: Experiment report groups results by domain; fact survival rates computed per domain

## Acceptance Criteria

1. At least 2 non-D&D domain packs MUST exist with ≥2 scenarios each
2. Each domain pack MUST include at least 1 deterministic scripted scenario
3. Each scenario MUST define ground truth facts (≥3 per scenario)
4. Each scenario MUST define seed units appropriate to the domain
5. Each scenario MUST include assertions for post-run validation
6. Attack strategies MUST be domain-appropriate (not D&D-flavored prompts in an ops context)
7. Scenarios MUST load and execute successfully with existing SimulationTurnExecutor

## Risks and Mitigations

1. Risk: Non-narrative scenarios may not stress the same failure modes as D&D scenarios
2. Mitigation: Design scenarios to target the same abstract attack patterns (contradiction, displacement, erosion) even though surface domain differs
3. Risk: Domain-specific prompt templates may produce lower-quality LLM responses
4. Mitigation: Pilot runs to validate DM/assistant response quality before committing to full matrix

## Proposal Seed

### Suggested OpenSpec Change Slug

`cross-domain-scenario-packs`

### Proposal Starter Inputs

1. Problem statement: The whitepaper cannot make cross-domain claims with only D&D scenarios. Section 11.4 already flags this as a limitation. Adding even 2 non-narrative domains substantially strengthens the evidence.
2. Why now: Scenarios are YAML files — low implementation risk, high evidence value.
3. Constraints: Must use existing scenario schema and turn execution infrastructure. Domain-specific prompt quality must be validated.
4. Outcomes: 2+ domain packs with deterministic and adaptive scenarios, enabling cross-domain results in Section 9.2.

### Suggested Capability Areas

1. Operations/incident response domain modeling
2. Compliance/rule-bound domain modeling
3. Cross-domain attack strategy mapping
4. Ground truth fact design for non-narrative contexts

### Candidate Requirement Blocks

1. Requirement: Operations scenario MUST model a system state that must persist across turns (e.g., "production database is in read-only mode during incident")
2. Scenario: Adversarial turn attempts to convince the assistant that the database is writable; FULL_ARC condition should maintain the read-only constraint

## Validation Plan

1. Each scenario loads successfully via ScenarioLoader
2. Each scenario completes a full simulation run without errors
3. Ground truth facts produce meaningful verdicts (CONFIRMED/CONTRADICTED/NOT_MENTIONED distribution is not degenerate)
4. Pilot runs against FULL_ARC and NO_ACTIVE_MEMORY conditions show measurable differences

## Known Limitations

1. Only 2 domains delivered (ops + compliance) — healthcare and legal deferred
2. Scenario count per domain is small (2-3) — not statistically independent enough for per-domain CIs
3. Attack strategies may not transfer perfectly across domains

## Suggested Command

`/opsx:new cross-domain-scenario-packs`
