## Why

Scripted scenarios test known, fixed attack sequences but don't respond to anchor state — if the DM holds firm on a fact, the adversary keeps hitting it anyway. The adaptive adversary engine from `tor` targets the weakest anchors at each turn, escalates strategy tier when attacks fail, and chains multi-turn SETUP → BUILD → PAYOFF sequences, giving us dynamic stress tests that reveal coverage gaps scripted YAML alone cannot.

## What Changes

- Port `tor`'s `adversary` package (7 classes) to `dev.dunnam.diceanchors.sim.engine.adversary`:
  - `AdversaryStrategy` — interface: `selectAttack(state, history) → AttackPlan`
  - `AnchorStateView` — immutable snapshot of active/conflicted anchors + fact lifecycle states
  - `AnchorStateViewBuilder` — builds `AnchorStateView` from dice-anchors `AnchorEngine.inject()`
  - `AttackHistory` — mutable log of `AttackOutcome` records per simulation run
  - `AttackPlan` — immutable record: targetFacts, strategies, tier, rationale, sequenceId/phase
  - `TieredEscalationStrategy` — default implementation: targets weakest anchors, escalates tier on failure, multi-turn sequences
  - `AdaptiveAttackPrompter` — LLM-based in-character dialogue generator from `AttackPlan`
- Wire into `SimulationService`: when `scenario.effectiveAdversaryMode() == "adaptive"`, use `TieredEscalationStrategy` + `AdaptiveAttackPrompter` instead of `generateAdversarialMessage()`
- `adaptive-tavern-fire` (already imported) becomes a runnable scenario end-to-end

**Adaptations from tor** (dice-anchors API differences):
- `AnchorStateViewBuilder`: `AnchorEngine.inject(contextId)` → replaces `getActive()`/`getConflicted()`; no `FactLifecycleTracker` equivalent → `factLifecycles` always empty for now; `budgetUtilization` = `anchors.size() / 20.0`
- `AdaptiveAttackPrompter`: `SimulationScenario.PersonaConfig` replaces `PlayerPersona`; `List<String> conversationHistory` replaces `List<ConversationMessage>`

## Capabilities

### New Capabilities

- `adaptive-adversary-engine`: The full adversary subsystem — `AdversaryStrategy`, `AnchorStateView`, `AnchorStateViewBuilder`, `AttackHistory`, `AttackPlan`, `TieredEscalationStrategy`, `AdaptiveAttackPrompter` — plus the `SimulationService` wiring that activates them when `adversaryMode: adaptive`

### Modified Capabilities

_(none — adaptive mode is a new branch; scripted mode is unchanged)_

## Impact

- **New package**: `dev.dunnam.diceanchors.sim.engine.adversary` (7 classes)
- **Modified**: `SimulationService` — adaptive mode branch in the turn loop; maintains `AttackHistory` per run
- **Depends on**: `StrategyCatalog` + `DriftStrategyDefinition` + `StrategyTier` (already ported), `SimulationScenario.AdversaryConfig` (already added), `ChatModel` (already injected via `ChatModelHolder`)
- **No new Maven dependencies** — Spring AI (`ChatModel`) and `StrategyCatalog` already present
- **Enables**: `adaptive-tavern-fire` scenario to run with live escalating adversary rather than empty scripted turns
