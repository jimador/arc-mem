# Prompt Token Budget

## Overview

Configurable token budget for anchor prompt assembly that prevents anchor injection from crowding out system instructions and user context. Lower-authority anchors are truncated first when the budget is exceeded, while CANON facts and the RFC 2119 preamble are always preserved.

## Requirements

### Requirement: Token Counter SPI
- The system MUST provide a `TokenCounter` interface with `int estimate(String text)`
- The system MUST provide a `CharHeuristicTokenCounter` default implementation using chars/4 (matching existing project heuristic)
- The `TokenCounter` MUST be injectable as a Spring bean with `@ConditionalOnMissingBean`

### Requirement: Budget Enforcement
- The system MUST provide a `PromptBudgetEnforcer` that accepts a list of anchors, a token budget, and a `TokenCounter`
- When the total anchor block exceeds the token budget, the enforcer MUST drop entire anchors in authority priority order: PROVISIONAL first, then UNRELIABLE, then RELIABLE
- CANON anchors MUST never be dropped
- The RFC 2119 preamble and verification protocol MUST never be truncated
- If CANON anchors alone exceed the budget, all CANON anchors MUST still be included (budget is best-effort)
- The enforcer MUST return a result record containing: included anchors, excluded anchors, tokens used, whether budget was exceeded

### Requirement: Integration with AnchorsLlmReference
- `AnchorsLlmReference.getContent()` MUST respect the configured token budget when assembling the anchor block
- When budget is 0 or disabled, behavior MUST be identical to current (no truncation)
- The budget SHOULD be passable as a constructor parameter to `AnchorsLlmReference`

### Requirement: Configuration
- The system MUST provide a `dice-anchors.assembly.prompt-token-budget` property (int, default 0 = disabled)
- The property MUST be bindable via Spring Boot's `@ConfigurationProperties`
- A value of 0 MUST mean "no token budget enforcement"

### Requirement: Telemetry
- `ContextTrace` SHOULD be extended with: `budgetApplied` (boolean), `anchorsExcluded` (int)
- When budget enforcement truncates anchors, the trace SHOULD record how many were excluded

### Requirement: UI Controls
- The SimulationView settings panel MUST include a token budget input control
- The control SHOULD be a numeric input with a reasonable range (0-5000 tokens)
- A value of 0 in the UI MUST mean "disabled" (no budget enforcement)
- Budget changes MUST take effect on the next simulation turn (not retroactively)

## Scenarios

#### Scenario: Budget not exceeded
- Given 5 anchors totaling 400 estimated tokens
- And token budget is set to 1000
- When getContent() assembles the anchor block
- Then all 5 anchors are included, no truncation occurs

#### Scenario: Budget exceeded, PROVISIONAL dropped first
- Given 3 CANON anchors (300 tokens), 5 PROVISIONAL anchors (500 tokens)
- And token budget is set to 500
- When getContent() assembles the anchor block
- Then all 3 CANON anchors are included
- And PROVISIONAL anchors are dropped until the block fits within budget

#### Scenario: Budget disabled (default)
- Given token budget is 0
- When getContent() assembles the anchor block
- Then all anchors within count budget are included (existing behavior)

#### Scenario: CANON exceeds budget
- Given 10 CANON anchors totaling 1000 tokens
- And token budget is set to 500
- When getContent() assembles the anchor block
- Then all 10 CANON anchors are still included (best-effort, CANON never dropped)

#### Scenario: UI budget control
- Given a user sets token budget to 800 in SimulationView
- When the next simulation turn executes
- Then the anchor block is assembled with an 800-token budget

## Constitutional Alignment

This capability follows the project's policy-based architecture. Budget enforcement is configurable without code changes. The default (disabled) preserves backward compatibility. All new types use constructor injection and immutable records per CLAUDE.md.
