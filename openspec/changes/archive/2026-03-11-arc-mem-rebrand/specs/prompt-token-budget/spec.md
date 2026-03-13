## MODIFIED Requirements

### Requirement: Working-memory capacity enforcement
Token budget enforcement SHALL be documented as "working-memory capacity management". `PromptBudgetEnforcer` MAY be renamed to `CapacityEnforcer` (design decision). Authority-graduated templates remain unchanged in behavior. Documentation SHALL describe capacity enforcement as ensuring the working set fits within the LLM's context window.

#### Scenario: Budget documentation uses capacity framing
- **WHEN** documentation describes token budget enforcement
- **THEN** it SHALL use "working-memory capacity" rather than "budget enforcement"
