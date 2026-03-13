## ADDED Requirements

### Requirement: Tier-aware sort order within authority bands

The `PromptBudgetEnforcer` SHALL sort memory units within each authority band by: (1) `memoryTier` descending (HOT first), (2) `diceImportance` descending, (3) `rank` descending. This ordering SHALL determine both injection order and drop priority (last in sort order dropped first).

#### Scenario: HOT memory unit preferred over COLD at same authority

- **GIVEN** two RELIABLE memory units: Memory unit A (HOT, rank 650) and Memory unit B (COLD, rank 200)
- **WHEN** `PromptBudgetEnforcer` sorts the authority band
- **THEN** Memory unit A SHALL appear before Memory unit B in injection order

#### Scenario: Tier takes precedence over diceImportance within authority

- **GIVEN** two UNRELIABLE memory units: Memory unit A (WARM, diceImportance 0.9) and Memory unit B (HOT, diceImportance 0.3)
- **WHEN** `PromptBudgetEnforcer` sorts the authority band
- **THEN** Memory unit B (HOT) SHALL appear before Memory unit A (WARM)

#### Scenario: Same tier falls back to diceImportance

- **GIVEN** two RELIABLE memory units both in WARM tier: Memory unit A (diceImportance 0.8) and Memory unit B (diceImportance 0.4)
- **WHEN** `PromptBudgetEnforcer` sorts the authority band
- **THEN** Memory unit A SHALL appear before Memory unit B

### Requirement: Tier-aware budget drop order

When the token budget is exceeded, the `PromptBudgetEnforcer` SHALL drop memory units in this priority (dropped first = lowest priority):
1. PROVISIONAL COLD memory units (lowest priority)
2. PROVISIONAL WARM memory units
3. PROVISIONAL HOT memory units
4. UNRELIABLE COLD memory units
5. UNRELIABLE WARM memory units
6. UNRELIABLE HOT memory units
7. RELIABLE COLD memory units
8. RELIABLE WARM memory units
9. RELIABLE HOT memory units
10. CANON memory units (never dropped)

Within each authority+tier combination, drop by ascending `diceImportance`, then ascending `rank`.

#### Scenario: COLD dropped before WARM at same authority

- **GIVEN** token budget exceeded with UNRELIABLE memory units: Memory unit A (COLD) and Memory unit B (WARM)
- **WHEN** `PromptBudgetEnforcer` must drop one memory unit
- **THEN** Memory unit A (COLD) SHALL be dropped first

#### Scenario: Lower authority dropped before higher authority regardless of tier

- **GIVEN** token budget exceeded with: Memory unit A (PROVISIONAL, HOT) and Memory unit B (RELIABLE, COLD)
- **WHEN** `PromptBudgetEnforcer` must drop one memory unit
- **THEN** Memory unit A (PROVISIONAL) SHALL be dropped first, preserving authority primacy

### Requirement: CANON tier exclusion from drop

CANON memory units SHALL never be dropped regardless of their memory tier. The existing CANON immunity invariant SHALL be preserved.

#### Scenario: CANON COLD memory unit retained

- **GIVEN** token budget exceeded and a CANON memory unit in COLD tier
- **WHEN** `PromptBudgetEnforcer` evaluates drop candidates
- **THEN** the CANON memory unit SHALL NOT be dropped

### Requirement: ContextTrace tier metadata

The `ContextTrace` SHALL include tier distribution metadata: counts of HOT, WARM, and COLD memory units in the assembled prompt.

#### Scenario: Trace includes tier counts

- **GIVEN** an assembled prompt with 3 HOT, 5 WARM, and 2 COLD memory units
- **WHEN** `ContextTrace` is generated
- **THEN** it SHALL include `hotCount = 3`, `warmCount = 5`, `coldCount = 2`
