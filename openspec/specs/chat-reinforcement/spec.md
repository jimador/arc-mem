## Purpose

Define chat-turn reinforcement behavior for active anchors.

## ADDED Requirements

### Requirement: Anchor reinforcement during chat turns
The chat flow SHALL reinforce all active anchors after each successful LLM response. Reinforcement SHALL use the existing `AnchorEngine.reinforce()` method, which increments the reinforcement count, applies rank boost via `ThresholdReinforcementPolicy`, and MAY upgrade authority when thresholds are met.

#### Scenario: Anchors reinforced after chat turn
- **GIVEN** the chat context has 3 active anchors with ranks 500, 600, 700
- **WHEN** a chat turn completes successfully (LLM responds without error)
- **THEN** all 3 anchors have their reinforcement count incremented and rank recalculated

#### Scenario: Reinforcement rank boost visible in sidebar
- **GIVEN** an anchor at rank 500 with reinforcement count 0
- **WHEN** 3 chat turns complete
- **THEN** the anchor's rank has increased (per `ThresholdReinforcementPolicy`) and the sidebar reflects the new rank on next refresh
