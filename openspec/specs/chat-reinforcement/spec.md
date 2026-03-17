## Purpose

Define chat-turn reinforcement behavior for active ARC Working Memory Units (AWMUs).

## ADDED Requirements

### Requirement: AWMU reinforcement during chat turns
The chat flow SHALL reinforce all active AWMUs after each successful LLM response. Reinforcement SHALL use the existing `ArcMemEngine.reinforce()` method, which increments the reinforcement count, applies rank boost via `ThresholdReinforcementPolicy`, and MAY upgrade authority when thresholds are met.

#### Scenario: AWMUs reinforced after chat turn
- **GIVEN** the chat context has 3 active AWMUs with ranks 500, 600, 700
- **WHEN** a chat turn completes successfully (LLM responds without error)
- **THEN** all 3 AWMUs have their reinforcement count incremented and rank recalculated

#### Scenario: Reinforcement rank boost visible in sidebar
- **GIVEN** a AWMU at rank 500 with reinforcement count 0
- **WHEN** 3 chat turns complete
- **THEN** the AWMU's rank has increased (per `ThresholdReinforcementPolicy`) and the sidebar reflects the new rank on next refresh
