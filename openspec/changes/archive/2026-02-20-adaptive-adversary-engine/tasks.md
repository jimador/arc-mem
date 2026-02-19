## 1. Core Data Types (adversary package)

- [x] 1.1 Create `AttackSequence` record (`String id`, `String phase`) in `dev.dunnam.diceanchors.sim.engine.adversary`
- [x] 1.2 Create `AttackPlan` record (`List<String> targetFacts`, `List<AttackStrategy> strategies`, `StrategyTier tier`, `String rationale`, `@Nullable AttackSequence sequence`) with non-empty strategies invariant (I1)
- [x] 1.3 Create `AttackOutcome` record (`int turn`, `AttackPlan plan`, `String verdictSeverity`) with a computed `succeeded()` method (`!"NONE".equals(verdictSeverity)`); carries the full plan rather than unpacking strategies/targets separately
- [x] 1.4 Create `AttackHistory` class with `recordOutcome(AttackOutcome)`, `lastN(int) → List<AttackOutcome>`, empty at construction
- [x] 1.5 Create `AdversaryStrategy` interface: `AttackPlan selectAttack(List<Anchor> active, List<Anchor> conflicted, AttackHistory history)`

## 2. TieredEscalationStrategy

- [x] 2.1 Create `TieredEscalationStrategy` implementing `AdversaryStrategy`, taking `AdversaryConfig` and `StrategyCatalog` in constructor
- [x] 2.2 Implement target selection: sort active anchors by rank ascending, skip those in the conflicted list, take `ceil(aggressiveness * eligibleCount)` targets (minimum 1)
- [x] 2.3 Implement tier escalation: per-target — for each selected target, find the most recent `AttackOutcome` in history that targeted it; if that outcome failed (`verdictSeverity == "NONE"`), escalate its tier by one; take the max tier across all targets, capped at `AdversaryConfig.maxEscalationTier`
- [x] 2.4 Implement strategy selection: prefer `AdversaryConfig.preferredStrategies` at the current tier; fall back to any strategy at that tier from `StrategyCatalog`
- [x] 2.5 Implement multi-turn sequence logic: generate a sequenceId when starting a new SETUP → BUILD → PAYOFF chain; advance phase on each subsequent call until PAYOFF; reset after payoff

## 3. AdaptiveAttackPrompter

- [x] 3.1 Create `AdaptiveAttackPrompter` taking `ChatModelHolder` and `StrategyCatalog` in constructor (`StrategyCatalog` needed for strategy display names in the user prompt)
- [x] 3.2 Implement `generateMessage(AttackPlan, SimulationScenario.PersonaConfig, List<String> conversationHistory) → String`
- [x] 3.3 Build system prompt from persona (name, description, playStyle, goals) and attack rationale
- [x] 3.4 Build user prompt referencing `AttackPlan.targetFacts` and strategy display names from `StrategyCatalog`
- [x] 3.5 Call `ChatModel` once and return the response text

## 4. SimulationService wiring

- [x] 4.1 Add `AttackHistory` field (nullable or per-run) to the simulation run state in `SimulationService`; initialize fresh at run start
- [x] 4.2 At the start of each turn in `SimulationService.runTurn()`, check `scenario.effectiveAdversaryMode().equals("adaptive")`
- [x] 4.3 In the adaptive branch: call `AnchorEngine.detectConflicts()` to get conflicted anchors, call `TieredEscalationStrategy.selectAttack()`, call `AdaptiveAttackPrompter.generateMessage()`
- [x] 4.4 After the DM responds in adaptive mode, call `AttackHistory.recordOutcome()` with an `AttackOutcome(turnNumber, currentPlan, verdictSeverity)` where `verdictSeverity` is derived from the turn's worst evaluation verdict
- [x] 4.5 Ensure the scripted branch is unmodified when `effectiveAdversaryMode()` returns `"scripted"`

## 5. Verification

- [x] 5.1 Run `./mvnw.cmd test` — all existing tests pass (no regressions)
- [x] 5.2 Start the app and run `adaptive-tavern-fire` end-to-end; confirm all turns produce player messages and DM responses with no exceptions
- [x] 5.3 Confirm `AttackHistory` grows turn-by-turn in `adaptive-tavern-fire` (add a debug log line if needed to verify)
- [x] 5.4 Run a scripted scenario (e.g., `adversarial-poisoned-player`) and confirm it still completes correctly with no change in behavior
