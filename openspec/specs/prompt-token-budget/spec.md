## MODIFIED Requirements

### Requirement: Budget Enforcement

- The system MUST provide a `PromptBudgetEnforcer` that accepts a list of memory units, a token budget, and a `TokenCounter`
- When the total memory unit block exceeds the token budget, the enforcer MUST drop entire memory units in authority priority order: PROVISIONAL first, then UNRELIABLE, then RELIABLE
- CANON memory units MUST never be dropped
- The RFC 2119 preamble and verification protocol MUST never be truncated
- If CANON memory units alone exceed the budget, all CANON memory units MUST still be included (budget is best-effort)
- The enforcer MUST return a result record containing: included memory units, excluded memory units, tokens used, whether budget was exceeded
- When adaptive footprint is enabled, the enforcer MUST estimate per-unit token cost using the authority-graduated template for that memory unit's authority level, rather than the uniform template estimate
- When adaptive footprint is disabled, the enforcer MUST use the existing uniform token estimation (current behavior, unchanged)

#### Scenario: Budget not exceeded

- **GIVEN** 5 memory units totaling 400 estimated tokens
- **AND** token budget is set to 1000
- **WHEN** `enforce()` assembles the memory unit block
- **THEN** all 5 memory units are included, no truncation occurs

#### Scenario: Budget exceeded, PROVISIONAL dropped first

- **GIVEN** 3 CANON memory units (300 tokens), 5 PROVISIONAL memory units (500 tokens)
- **AND** token budget is set to 500
- **WHEN** `enforce()` assembles the memory unit block
- **THEN** all 3 CANON memory units are included
- **AND** PROVISIONAL memory units are dropped until the block fits within budget

#### Scenario: Budget disabled (default)

- **GIVEN** token budget is 0
- **WHEN** `enforce()` assembles the memory unit block
- **THEN** all memory units within count budget are included (existing behavior)

#### Scenario: CANON exceeds budget

- **GIVEN** 10 CANON memory units totaling 1000 tokens
- **AND** token budget is set to 500
- **WHEN** `enforce()` assembles the memory unit block
- **THEN** all 10 CANON memory units are still included (best-effort, CANON never dropped)

#### Scenario: Adaptive footprint uses authority-aware estimation

- **GIVEN** adaptive footprint is enabled
- **AND** a CANON memory unit and a PROVISIONAL memory unit have the same text length
- **WHEN** the enforcer estimates tokens for each
- **THEN** the CANON memory unit's token estimate is lower than the PROVISIONAL memory unit's estimate
- **AND** the difference reflects the template verbosity difference between CANON (minimal) and PROVISIONAL (full detail)

#### Scenario: Adaptive footprint disabled uses uniform estimation

- **GIVEN** adaptive footprint is disabled
- **AND** a CANON memory unit and a PROVISIONAL memory unit have the same text length
- **WHEN** the enforcer estimates tokens for each
- **THEN** both memory units have the same token estimate (current behavior)
