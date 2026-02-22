## ADDED Requirements

### Requirement: Tier-aware sort order within authority bands

The `PromptBudgetEnforcer` SHALL sort anchors within each authority band by: (1) `memoryTier` descending (HOT first), (2) `diceImportance` descending, (3) `rank` descending. This ordering SHALL determine both injection order and drop priority (last in sort order dropped first).

#### Scenario: HOT anchor preferred over COLD at same authority

- **GIVEN** two RELIABLE anchors: Anchor A (HOT, rank 650) and Anchor B (COLD, rank 200)
- **WHEN** `PromptBudgetEnforcer` sorts the authority band
- **THEN** Anchor A SHALL appear before Anchor B in injection order

#### Scenario: Tier takes precedence over diceImportance within authority

- **GIVEN** two UNRELIABLE anchors: Anchor A (WARM, diceImportance 0.9) and Anchor B (HOT, diceImportance 0.3)
- **WHEN** `PromptBudgetEnforcer` sorts the authority band
- **THEN** Anchor B (HOT) SHALL appear before Anchor A (WARM)

#### Scenario: Same tier falls back to diceImportance

- **GIVEN** two RELIABLE anchors both in WARM tier: Anchor A (diceImportance 0.8) and Anchor B (diceImportance 0.4)
- **WHEN** `PromptBudgetEnforcer` sorts the authority band
- **THEN** Anchor A SHALL appear before Anchor B

### Requirement: Tier-aware budget drop order

When the token budget is exceeded, the `PromptBudgetEnforcer` SHALL drop anchors in this priority (dropped first = lowest priority):
1. PROVISIONAL COLD anchors (lowest priority)
2. PROVISIONAL WARM anchors
3. PROVISIONAL HOT anchors
4. UNRELIABLE COLD anchors
5. UNRELIABLE WARM anchors
6. UNRELIABLE HOT anchors
7. RELIABLE COLD anchors
8. RELIABLE WARM anchors
9. RELIABLE HOT anchors
10. CANON anchors (never dropped)

Within each authority+tier combination, drop by ascending `diceImportance`, then ascending `rank`.

#### Scenario: COLD dropped before WARM at same authority

- **GIVEN** token budget exceeded with UNRELIABLE anchors: Anchor A (COLD) and Anchor B (WARM)
- **WHEN** `PromptBudgetEnforcer` must drop one anchor
- **THEN** Anchor A (COLD) SHALL be dropped first

#### Scenario: Lower authority dropped before higher authority regardless of tier

- **GIVEN** token budget exceeded with: Anchor A (PROVISIONAL, HOT) and Anchor B (RELIABLE, COLD)
- **WHEN** `PromptBudgetEnforcer` must drop one anchor
- **THEN** Anchor A (PROVISIONAL) SHALL be dropped first, preserving authority primacy

### Requirement: CANON tier exclusion from drop

CANON anchors SHALL never be dropped regardless of their memory tier. The existing CANON immunity invariant SHALL be preserved.

#### Scenario: CANON COLD anchor retained

- **GIVEN** token budget exceeded and a CANON anchor in COLD tier
- **WHEN** `PromptBudgetEnforcer` evaluates drop candidates
- **THEN** the CANON anchor SHALL NOT be dropped

### Requirement: ContextTrace tier metadata

The `ContextTrace` SHALL include tier distribution metadata: counts of HOT, WARM, and COLD anchors in the assembled prompt.

#### Scenario: Trace includes tier counts

- **GIVEN** an assembled prompt with 3 HOT, 5 WARM, and 2 COLD anchors
- **WHEN** `ContextTrace` is generated
- **THEN** it SHALL include `hotCount = 3`, `warmCount = 5`, `coldCount = 2`
