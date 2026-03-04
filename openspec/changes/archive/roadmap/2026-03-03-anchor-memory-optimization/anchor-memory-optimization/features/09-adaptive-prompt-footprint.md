# Feature: Adaptive Prompt Footprint

## Feature ID

`F09`

## Summary

Implement an inverse relationship between anchor authority and prompt footprint -- higher-authority anchors get less prompt space, not more. The insight from sleeping-llm is that as knowledge consolidates (MEMIT scale-down 1.0 to 0.5 to 0.1 to 0.0), the original injection mechanism can be reduced because the model has internalized the knowledge. For prompt-based systems, this means well-established anchors need less verbose reminding while newer propositions need maximum context.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: All anchors currently use the same prompt template regardless of authority. This wastes token budget on well-established knowledge that the model is repeatedly reminded of in verbose form, while leaving less room for newer, less-established propositions that need maximum detail to be respected.
2. Value delivered: Authority-graduated prompt templates free token budget by condensing established anchors, allowing more new propositions to be included within the same token budget. Research (sleeping-llm MEMIT scale-down) shows consolidated knowledge can reduce its injection footprint as it stabilizes.
3. Why now: Wave 3 -- no hard dependencies. As anchor sets grow and mature, token budget becomes the binding constraint. This feature is a direct optimization on prompt assembly that requires no infrastructure changes.

## Scope

### In Scope

1. Define authority-graduated prompt templates with decreasing verbosity:
   - PROVISIONAL: Full text + context + source citation (maximum detail).
   - UNRELIABLE: Full text + brief context.
   - RELIABLE: Condensed text (key assertion only).
   - CANON: Minimal reference (identifier + short assertion).
2. Integrate authority-based template selection into `AnchorsLlmReference` during prompt assembly.
3. Update `PromptBudgetEnforcer` token estimation to account for authority-dependent template sizes.
4. Make template selection configurable (opt-in, with full-verbosity as default behavior).
5. Measure compliance impact per authority tier.
6. Start with CANON-only reduction; extend to RELIABLE if compliance impact is acceptable.

### Out of Scope

1. Dynamic per-anchor template selection based on conversation context (only authority drives template choice).
2. Changes to `CompliancePolicy` (F03 compliance enforcement layer handles compliance monitoring; this feature only adjusts prompt verbosity).
3. Retrieval mode changes (BULK, HYBRID, TOOL modes are orthogonal to template verbosity).
4. New prompt template file format or templating engine.

## Dependencies

1. Feature dependencies: None.
2. Technical prerequisites: `AnchorsLlmReference`, `PromptBudgetEnforcer`, `TokenCounter`, `PromptTemplates`, `PromptPathConstants`, `CompliancePolicy`.
3. Priority: SHOULD.
4. OpenSpec change slug: `adaptive-prompt-footprint`.
5. Research rec: D.

## Research Requirements

1. Open questions:
   - What template content for each authority tier balances token savings against compliance risk?
   - What compliance threshold for CANON reduction is acceptable (how much compliance degradation, if any, is tolerable)?
   - How does template reduction interact with retrieval modes (BULK vs. HYBRID vs. TOOL)? Does HYBRID mode's relevance scoring compensate for reduced template detail?
   - Does the graduated approach (CANON-first, then RELIABLE) produce measurable token savings, or is the savings marginal because CANON anchors are few?
2. Required channels: `codebase`
3. Research completion gate: Template prototypes for CANON and RELIABLE tiers SHOULD be tested against at least one simulation scenario measuring compliance rate before full implementation.

## Impacted Areas

1. Packages/components: `assembly/` (`AnchorsLlmReference` template selection, `PromptBudgetEnforcer` token estimation), `prompt/` (new authority-graduated template files).
2. Data/persistence: No schema changes.
3. Domain-specific subsystem impacts: Simulation results may differ when adaptive footprint is enabled (different prompt content affects LLM responses). Benchmarks SHOULD compare uniform vs. adaptive footprint.

## Visibility Requirements

### UI Visibility

1. User-facing surface: RunInspectorView MAY display per-anchor template tier used in prompt assembly. ContextInspectorPanel SHOULD show token savings from adaptive footprint.
2. What is shown: Token count per authority tier, total token savings compared to uniform templates.
3. Success signal: More anchors fit within the same token budget; budget enforcement drops fewer anchors.

### Observability Visibility

1. Logs/events/metrics: Tokens per authority tier. Total token savings per prompt assembly. Compliance rate per authority tier. Budget enforcement drop count comparison (adaptive vs. uniform). Logger SHOULD emit template selection summary at DEBUG level.
2. Trace/audit payload: Per-anchor template tier assignment in `ContextTrace`. Token count breakdown by authority in `BudgetResult`.
3. How to verify: Compare token utilization and anchor inclusion counts between uniform and adaptive configurations on the same simulation scenario.

## Acceptance Criteria

1. Prompt footprint per anchor MUST vary by authority level when adaptive footprint is enabled.
2. PROVISIONAL anchors MUST use the most detailed prompt template (full text + context + source citation).
3. CANON anchors SHOULD use a condensed prompt template (minimal reference).
4. Token budget utilization SHOULD improve -- more anchors fit within the same token budget.
5. Compliance rate for CANON anchors MUST NOT degrade beyond a configurable threshold.
6. Template selection MUST be configurable (opt-in, with full-verbosity as default).
7. `PromptBudgetEnforcer` MUST account for authority-dependent template sizes in token estimation.
8. The graduated approach MUST start with CANON-only reduction, with RELIABLE reduction gated on compliance validation.

## Risks and Mitigations

1. Risk: Reducing prompt detail for CANON anchors could reduce compliance if the model has not "internalized" the knowledge (each API call is a new context window -- there is no true internalization).
   Mitigation: Compliance enforcement layer (F03) catches violations. CANON-first graduated approach limits blast radius. Configurable compliance threshold triggers fallback to full template.
2. Risk: Token savings are marginal because CANON anchors are typically few in number.
   Mitigation: Extending to RELIABLE anchors (the most numerous tier in stable conversations) provides the bulk of savings. CANON-first approach validates the pattern before scaling.
3. Risk: Template content is hard to get right -- too terse and the model ignores the anchor; too verbose and savings disappear.
   Mitigation: A/B testable via simulation harness. Template content is external (prompt files), not hardcoded.
4. Risk: Different LLMs respond differently to condensed prompts.
   Mitigation: Template selection is configurable per deployment; compliance monitoring detects model-specific issues.

## Proposal Seed

### Suggested OpenSpec Change Slug

`adaptive-prompt-footprint`

### Proposal Starter Inputs

1. Problem statement: All anchors use the same prompt template regardless of authority. This wastes token budget on well-established knowledge that the model is repeatedly reminded of, while leaving less room for newer, less-established propositions. Research (sleeping-llm) shows consolidated knowledge can reduce its injection footprint as it stabilizes. The MEMIT scale-down schedule (1.0 to 0.5 to 0.1 to 0.0) as LoRA absorbs knowledge directly parallels authority-graduated prompt verbosity.
2. Why now: As anchor sets grow and mature, token budget becomes the binding constraint. Freeing budget by condensing established anchors allows more new propositions to be included.
3. Constraints: MUST be opt-in initially (default = current uniform templates). MUST be measurable via compliance enforcement (F03). SHOULD NOT change behavior for simulations unless explicitly configured.
4. Visible outcomes: Token savings per authority tier; more anchors included per prompt; compliance rate tracking by tier.

### Suggested Capability Areas

1. Authority-graduated prompt template design (4 tiers).
2. `AnchorsLlmReference` template selection during prompt assembly.
3. `PromptBudgetEnforcer` authority-aware token estimation.
4. Compliance impact measurement per authority tier.

### Candidate Requirement Blocks

1. Requirement: The prompt assembly layer SHALL select prompt templates based on anchor authority level when adaptive footprint is enabled.
2. Scenario: Given 15 active anchors (3 CANON, 5 RELIABLE, 4 UNRELIABLE, 3 PROVISIONAL) and a 2000-token budget, adaptive footprint SHALL include all 15 anchors where uniform templates would have required dropping 3 PROVISIONAL anchors for budget compliance.
3. Scenario: When a CANON anchor's compliance rate drops below the configurable threshold, the system SHALL fall back to the full-verbosity template for that anchor.

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| Rec D | MEMIT scale-down schedule (1.0 to 0.5 to 0.1 to 0.0) as LoRA absorbs knowledge. Higher-authority anchors could receive less prompt space since the LLM should already "know" them from repeated exposure. | `openspec/research/llm-optimization-external-research.md` sec 3.1 | Medium | Validates the inverse authority-footprint concept. Note: prompt-based systems lack true "internalization" -- each call is a new context window. This is the key risk. |
| Paper 6 | Per-fact graduated consolidation stages (0 to 3) with MEMIT dissolution at each stage. 100% advancement rate at 5-20 facts. | sleeping-llm Paper 6 (Zenodo 18779159) | High | Confirms that graduated reduction works for weight-edited systems. Prompt-based analogy requires compliance validation. |
| Takeaway 9 | Pathway separation matters -- the format of anchor context injection (system prompt vs. user message vs. structured template) may significantly affect compliance. | `openspec/research/llm-optimization-external-research.md` sec 5 | Medium | Template design choices (not just verbosity) may affect compliance. Template experimentation is warranted. |

## Validation Plan

1. Unit tests: Template selection logic (each authority tier maps to correct template). Token estimation accuracy with authority-dependent templates. Budget enforcement behavior with mixed-authority anchor sets.
2. Integration test: Prompt assembly with adaptive footprint enabled; verify correct template per authority tier; verify token budget compliance.
3. Compliance validation: Run adversarial simulation scenarios with adaptive footprint enabled; measure per-tier compliance rates; compare against uniform-template baseline.
4. Token savings validation: Measure anchor inclusion counts and token utilization with and without adaptive footprint on identical scenarios.

## Known Limitations

1. Prompt-based systems have no true "internalization" -- each LLM call is a new context window. The sleeping-llm analogy (MEMIT dissolution as LoRA absorbs) does not directly translate. Compliance risk is real and MUST be monitored.
2. Optimal template content per tier is model-dependent. Different LLMs may need different levels of detail for the same authority tier.
3. Token savings from CANON-only reduction may be small if CANON anchors are few. The bulk of savings comes from RELIABLE reduction, which is gated on compliance validation.
4. Interaction between adaptive footprint and retrieval modes (BULK vs. HYBRID) is not fully characterized. HYBRID mode's relevance scoring may partially compensate for reduced detail.

## Suggested Command

`/opsx:new adaptive-prompt-footprint`
