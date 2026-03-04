## MODIFIED Requirements

### Requirement: Budget Enforcement

- The system MUST provide a `PromptBudgetEnforcer` that accepts a list of anchors, a token budget, and a `TokenCounter`
- When the total anchor block exceeds the token budget, the enforcer MUST drop entire anchors in authority priority order: PROVISIONAL first, then UNRELIABLE, then RELIABLE
- CANON anchors MUST never be dropped
- The RFC 2119 preamble and verification protocol MUST never be truncated
- If CANON anchors alone exceed the budget, all CANON anchors MUST still be included (budget is best-effort)
- The enforcer MUST return a result record containing: included anchors, excluded anchors, tokens used, whether budget was exceeded
- When adaptive footprint is enabled, the enforcer MUST estimate per-anchor token cost using the authority-graduated template for that anchor's authority level, rather than the uniform template estimate
- When adaptive footprint is disabled, the enforcer MUST use the existing uniform token estimation (current behavior, unchanged)

#### Scenario: Budget not exceeded

- **GIVEN** 5 anchors totaling 400 estimated tokens
- **AND** token budget is set to 1000
- **WHEN** `enforce()` assembles the anchor block
- **THEN** all 5 anchors are included, no truncation occurs

#### Scenario: Budget exceeded, PROVISIONAL dropped first

- **GIVEN** 3 CANON anchors (300 tokens), 5 PROVISIONAL anchors (500 tokens)
- **AND** token budget is set to 500
- **WHEN** `enforce()` assembles the anchor block
- **THEN** all 3 CANON anchors are included
- **AND** PROVISIONAL anchors are dropped until the block fits within budget

#### Scenario: Budget disabled (default)

- **GIVEN** token budget is 0
- **WHEN** `enforce()` assembles the anchor block
- **THEN** all anchors within count budget are included (existing behavior)

#### Scenario: CANON exceeds budget

- **GIVEN** 10 CANON anchors totaling 1000 tokens
- **AND** token budget is set to 500
- **WHEN** `enforce()` assembles the anchor block
- **THEN** all 10 CANON anchors are still included (best-effort, CANON never dropped)

#### Scenario: Adaptive footprint uses authority-aware estimation

- **GIVEN** adaptive footprint is enabled
- **AND** a CANON anchor and a PROVISIONAL anchor have the same text length
- **WHEN** the enforcer estimates tokens for each
- **THEN** the CANON anchor's token estimate is lower than the PROVISIONAL anchor's estimate
- **AND** the difference reflects the template verbosity difference between CANON (minimal) and PROVISIONAL (full detail)

#### Scenario: Adaptive footprint disabled uses uniform estimation

- **GIVEN** adaptive footprint is disabled
- **AND** a CANON anchor and a PROVISIONAL anchor have the same text length
- **WHEN** the enforcer estimates tokens for each
- **THEN** both anchors have the same token estimate (current behavior)
