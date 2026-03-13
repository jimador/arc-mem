## Purpose

Define chat-turn reinforcement behavior for active memory units.

## ADDED Requirements

### Requirement: Memory unit reinforcement during chat turns
The chat flow SHALL reinforce all active memory units after each successful LLM response. Reinforcement SHALL use the existing `ArcMemEngine.reinforce()` method, which increments the reinforcement count, applies rank boost via `ThresholdReinforcementPolicy`, and MAY upgrade authority when thresholds are met.

#### Scenario: Memory units reinforced after chat turn
- **GIVEN** the chat context has 3 active memory units with ranks 500, 600, 700
- **WHEN** a chat turn completes successfully (LLM responds without error)
- **THEN** all 3 memory units have their reinforcement count incremented and rank recalculated

#### Scenario: Reinforcement rank boost visible in sidebar
- **GIVEN** a memory unit at rank 500 with reinforcement count 0
- **WHEN** 3 chat turns complete
- **THEN** the memory unit's rank has increased (per `ThresholdReinforcementPolicy`) and the sidebar reflects the new rank on next refresh
