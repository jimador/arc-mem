## ADDED Requirements

### Requirement: AdversaryStrategy interface
The system SHALL define `AdversaryStrategy` as a single-method interface:
`selectAttack(List<Anchor> active, List<Anchor> conflicted, AttackHistory history) → AttackPlan`.
All adaptive adversary implementations MUST satisfy this contract.

#### Scenario: Strategy receives full anchor state
- **GIVEN** a simulation run in adaptive mode
- **WHEN** the adversary selects an attack for turn N
- **THEN** it receives the current active anchor list (from `AnchorEngine.inject()`), the current conflicted anchor list (from `AnchorEngine.detectConflicts()`), and the full `AttackHistory` for this run

#### Scenario: Strategy produces a non-null plan
- **WHEN** `selectAttack()` is called with a non-empty active anchor list
- **THEN** it returns a non-null `AttackPlan` with at least one target and at least one strategy

---

### Requirement: AttackPlan is an immutable typed record
`AttackPlan` SHALL be an immutable record carrying: `List<String> targetFacts`, `List<AttackStrategy> strategies`, `StrategyTier tier`, `String rationale`, `@Nullable AttackSequence sequence`.
String-based tier levels and string-based strategy IDs SHALL NOT be used.

#### Scenario: AttackPlan uses enum types
- **WHEN** `TieredEscalationStrategy` produces an `AttackPlan`
- **THEN** `strategies` contains `AttackStrategy` enum values and `tier` is a `StrategyTier` enum value

#### Scenario: AttackPlan optional sequence
- **WHEN** an attack is a standalone turn (not part of a multi-turn sequence)
- **THEN** `AttackPlan.sequence()` is null

#### Scenario: AttackPlan multi-turn sequence
- **WHEN** an attack is the BUILD phase of a sequence with id "seq-1"
- **THEN** `AttackPlan.sequence()` is `AttackSequence("seq-1", "BUILD")`

---

### Requirement: AttackSequence models multi-turn attack phases
`AttackSequence` SHALL be an immutable record with fields `String id` (opaque sequence identifier) and `String phase` (one of SETUP, BUILD, PAYOFF).
Paired nullable fields `sequenceId`/`sequencePhase` SHALL NOT be used.

#### Scenario: Sequence phase progression
- **GIVEN** a multi-turn attack sequence started by `TieredEscalationStrategy`
- **WHEN** the sequence has not yet been established
- **THEN** the first attack in the sequence has phase SETUP
- **WHEN** the setup has landed
- **THEN** subsequent attacks use phases BUILD and PAYOFF

---

### Requirement: AttackHistory records outcomes per simulation run
`AttackHistory` SHALL be a mutable log scoped to one simulation run.
It SHALL support: `recordOutcome(AttackOutcome)`, `lastN(int n) → List<AttackOutcome>`.
`AttackOutcome` SHALL be an immutable record with: `int turn`, `AttackPlan plan`, `String verdictSeverity`.
It SHALL expose a computed `succeeded()` method returning `true` when `verdictSeverity` is not `"NONE"`.
The full `AttackPlan` is carried rather than unpacking `strategiesUsed`/`targetsAttempted` separately, so callers access `outcome.plan().strategies()` and `outcome.plan().targetFacts()` directly.

#### Scenario: Outcome recorded after each adaptive turn
- **GIVEN** an adaptive simulation run
- **WHEN** the DM responds to an adversarial player message
- **THEN** an `AttackOutcome` is recorded in `AttackHistory` with the correct turn number, the `AttackPlan` used, and the `verdictSeverity` derived from the turn's worst evaluation verdict

#### Scenario: History is empty at run start
- **WHEN** a new adaptive simulation run begins
- **THEN** `AttackHistory.lastN(10)` returns an empty list

#### Scenario: lastN caps at available history
- **WHEN** only 3 outcomes have been recorded
- **THEN** `AttackHistory.lastN(10)` returns all 3 outcomes, not an error

---

### Requirement: TieredEscalationStrategy targets weakest active anchors
`TieredEscalationStrategy` SHALL target anchors with the lowest rank among the active anchor list, skipping anchors already in the conflicted list.
The number of anchors targeted per turn SHALL scale with `AdversaryConfig.aggressiveness` (0.0–1.0).

#### Scenario: Weakest anchors targeted first
- **GIVEN** active anchors with ranks [800, 300, 150, 500]
- **WHEN** `selectAttack()` is called with aggressiveness 0.5
- **THEN** the plan targets the anchor(s) with lowest rank (150, 300)

#### Scenario: Conflicted anchors skipped
- **GIVEN** active anchors [rank 150, rank 300] where rank-150 is also in the conflicted list
- **WHEN** `selectAttack()` is called
- **THEN** the plan targets rank-300, not rank-150

#### Scenario: Tier escalates after a failed attack on a target
- **GIVEN** the most recent outcome targeting anchor X has `verdictSeverity == "NONE"` (attack had no effect)
- **WHEN** `selectAttack()` is called and X is again selected as a target
- **THEN** the tier for X is incremented by one relative to the tier used in that last outcome

#### Scenario: Tier cap respected
- **GIVEN** `AdversaryConfig.maxEscalationTier = 2` (INTERMEDIATE)
- **WHEN** attack failures would normally escalate to ADVANCED
- **THEN** the plan tier remains at INTERMEDIATE (StrategyTier.INTERMEDIATE)

#### Scenario: Strategy drawn from catalog preferred list
- **GIVEN** `AdversaryConfig.preferredStrategies = ["FALSE_MEMORY_PLANT", "AUTHORITY_HIJACK"]`
- **WHEN** both strategies are available at the current tier
- **THEN** the plan prefers those strategies over other same-tier strategies

---

### Requirement: AdaptiveAttackPrompter generates in-character player dialogue
`AdaptiveAttackPrompter` SHALL accept an `AttackPlan`, `SimulationScenario.PersonaConfig`, and `List<String> conversationHistory`, call `ChatModel` once, and return a `String` player message that embeds the attack in natural in-character dialogue.

#### Scenario: Dialogue reflects target facts
- **GIVEN** an `AttackPlan` targeting fact "The tavern burned three nights ago"
- **WHEN** `generateMessage()` is called
- **THEN** the returned player message references or challenges the targeted fact in character

#### Scenario: Persona voice applied
- **GIVEN** a persona with playStyle "aggressive merchant"
- **WHEN** `generateMessage()` is called
- **THEN** the returned dialogue reflects an aggressive merchant voice, not generic narration

---

### Requirement: SimulationService branches on adversaryMode
`SimulationService` SHALL inspect `scenario.effectiveAdversaryMode()` at the start of each turn.
When the value is `"adaptive"`, it SHALL use `TieredEscalationStrategy` + `AdaptiveAttackPrompter` to generate the player message.
When the value is `"scripted"` (default), the existing scripted path SHALL be used without change.

#### Scenario: Adaptive path activates for adversaryMode adaptive
- **GIVEN** a scenario with `adversaryMode: adaptive`
- **WHEN** a simulation turn executes
- **THEN** `TieredEscalationStrategy.selectAttack()` is called and its result drives `AdaptiveAttackPrompter`

#### Scenario: Scripted path unaffected
- **GIVEN** a scenario with no `adversaryMode` field (defaults to scripted)
- **WHEN** a simulation turn executes
- **THEN** the existing scripted turn logic runs and `TieredEscalationStrategy` is never called

#### Scenario: AttackHistory maintained across turns
- **GIVEN** an adaptive simulation with 5 turns completed
- **WHEN** turn 6 begins
- **THEN** `TieredEscalationStrategy` receives an `AttackHistory` containing the outcomes from turns 1–5

#### Scenario: adaptive-tavern-fire runs end-to-end
- **GIVEN** the `adaptive-tavern-fire` scenario (adversaryMode: adaptive, no scripted turns)
- **WHEN** the simulation is started in SimulationView
- **THEN** it completes all turns without error, each turn producing a player message and DM response

---

## Invariants

- **I1**: `AttackPlan.strategies` is non-empty
- **I2**: `AttackPlan.tier` matches the tier of every strategy in `AttackPlan.strategies` per `StrategyCatalog` (convention — enforced transitively by `TieredEscalationStrategy.selectStrategies()`, not checked in the record constructor)
- **I3**: `AttackHistory` is scoped to a single simulation run and discarded when the run ends
- **I4**: `TieredEscalationStrategy` never produces a tier above `AdversaryConfig.maxEscalationTier`
- **I5**: `AdaptiveAttackPrompter` makes exactly one `ChatModel` call per `generateMessage()` invocation
