### Requirement: 13 ported scenarios

Thirteen simulation scenarios SHALL be ported from tor and adapted to the dice-anchors `SimulationScenario` format. The scenarios SHALL be organized in `src/main/resources/simulations/` as YAML files. The 13 scenarios SHALL be: `trust-evaluation-basic`, `trust-evaluation-full-signals`, `adversarial-contradictory`, `adversarial-poisoned-player`, `adversarial-displacement`, `dungeon-of-mirrors`, `dead-kingdom`, `compaction-stress`, `balanced-campaign`, `narrative-dm-driven`, `dormancy-revival`, `episodic-recall`, and `multi-session-campaign`.

#### Scenario: All 13 scenario files exist
- **WHEN** the `src/main/resources/simulations/` directory is listed
- **THEN** it contains YAML files for all 13 scenarios plus the existing `cursed-blade.yml` and `unit-drift.yml`

#### Scenario: Scenarios load without error
- **WHEN** `ScenarioLoader.listScenarios()` is called
- **THEN** all 15 scenarios (13 new + 2 existing) are returned without parsing errors

### Requirement: Setting as ground truth source for scene-setting extraction

The scenario `setting` field SHALL contain sufficient factual detail for the DM to naturally state ground truth facts during the scene-setting turn 0. For scenarios without seed units or scripted establish turns, the setting is the primary mechanism by which ground truth facts enter the conversation and become available for DICE extraction. Scenario authors SHOULD ensure that all ground truth facts are represented in the setting text so the DM can reference them when narrating the scene.

#### Scenario: Setting contains ground truth details for extraction

- **GIVEN** a scenario with 5 ground truth facts and no seed units
- **WHEN** the setting text includes all 5 facts as narrative details
- **THEN** the DM's scene-setting response is likely to mention those facts, enabling DICE extraction to capture initial propositions

### Requirement: Extended SimulationScenario format

The `SimulationScenario` record SHALL be extended with fields for: `trustEvaluation` (trust profile config), `seedUnits` (already exists), `dormancyConfig` (topic decay settings), and `sessions` (named session boundaries for multi-session scenarios). The `@JsonIgnoreProperties(ignoreUnknown = true)` annotation SHALL ensure backward compatibility with existing scenarios that lack the new fields. New fields SHALL default to null or empty when not specified in YAML.

#### Scenario: Backward compatibility with cursed-blade.yml
- **WHEN** the existing `cursed-blade.yml` is loaded (which has no trust or dormancy config)
- **THEN** the scenario loads successfully with trustEvaluation=null and dormancyConfig=null

#### Scenario: Multi-session scenario parsed
- **WHEN** `multi-session-campaign.yml` is loaded with 3 named sessions
- **THEN** the `sessions` field contains 3 session entries with name, startTurn, and endTurn

### Requirement: ScenarioLoader extensions

`ScenarioLoader` SHALL be extended to handle the new scenario features. The loader SHALL parse trust profile references and resolve them to `DomainProfile` instances. The loader SHALL parse dormancy configuration (decay rate, revival threshold, dormancy turns). The loader SHALL validate session boundaries do not overlap and cover the full turn range.

#### Scenario: Trust profile resolved
- **WHEN** a scenario specifies `trustEvaluation: { profile: SECURE }`
- **THEN** `ScenarioLoader` resolves the profile name to the SECURE `DomainProfile` instance

#### Scenario: Invalid session boundaries rejected
- **WHEN** a scenario specifies overlapping session boundaries (session 1: turns 1-10, session 2: turns 8-20)
- **THEN** `ScenarioLoader` throws a validation error identifying the overlap

### Requirement: Turn types

`SimulationTurnExecutor` SHALL support the following turn types in the scripted turn `type` field: `ESTABLISH` (present factual content for the DM to adopt), `DISPLACEMENT` (attempt to displace an established fact with a replacement), `DRIFT` (subtle reframing to shift fact interpretation), and `RECALL_PROBE` (ask the DM to recall a previously established fact). The turn type SHALL influence adversarial message generation and drift evaluation behavior. WARM_UP and ATTACK types SHALL continue to work as currently implemented.

#### Scenario: DISPLACEMENT turn generates targeted attack
- **WHEN** a scripted turn has type=DISPLACEMENT and targetFact="the-sword-is-cursed"
- **THEN** the adversarial message attempts to replace the targeted fact with a contradictory statement

#### Scenario: RECALL_PROBE evaluates memory
- **WHEN** a scripted turn has type=RECALL_PROBE
- **THEN** drift evaluation runs against all ground truth facts, checking if the DM recalls them accurately

### Requirement: Attack strategies

`SimulationTurnExecutor` SHALL support seven attack strategies in the scripted turn `strategy` field: `SUBTLE_REFRAME`, `CONFIDENT_ASSERTION`, `AUTHORITY_HIJACK`, `EMOTIONAL_OVERRIDE`, `FALSE_MEMORY_PLANT`, `TIME_SKIP_RECALL`, and `DETAIL_FLOOD`. Each strategy SHALL produce a distinct adversarial prompt pattern when generating adversarial messages. The strategy SHALL be included in OTEL span attributes when observability is active.

#### Scenario: AUTHORITY_HIJACK strategy prompt
- **WHEN** a turn uses the AUTHORITY_HIJACK strategy
- **THEN** the generated adversarial message claims a higher-authority source (e.g., "The head DM told me...")

#### Scenario: DETAIL_FLOOD strategy prompt
- **WHEN** a turn uses the DETAIL_FLOOD strategy
- **THEN** the generated adversarial message buries the targeted contradiction in extensive irrelevant detail

#### Scenario: Strategy recorded in turn data
- **WHEN** a turn with strategy=SUBTLE_REFRAME completes
- **THEN** the `SimulationTurn` record contains attackStrategy="SUBTLE_REFRAME"

### Requirement: Backward compatibility with existing scenarios

The existing `cursed-blade.yml` and `unit-drift.yml` scenarios SHALL continue to load and execute correctly without modification. Any field additions to `SimulationScenario` SHALL have safe defaults (null or empty). `ScenarioLoader` SHALL not require the new fields to be present.

#### Scenario: Existing scenario runs unmodified
- **WHEN** `cursed-blade.yml` is loaded and executed
- **THEN** the simulation completes successfully with the same behavior as before the changes

### Requirement: Two-level scenario selection by category
The scenario selection SHALL use two separate components: a Category `Select<String>` and a Scenario `ComboBox<SimulationScenario>`. Selecting a category SHALL filter the Scenario ComboBox to show only scenarios in that category. Categories SHALL be sorted alphabetically (via TreeMap), with scenarios sorted by title within each category.

#### Scenario: Category selection filters scenarios
- **WHEN** the user selects a category from the Category Select
- **THEN** the Scenario ComboBox is filtered to show only scenarios belonging to that category, sorted by title

#### Scenario: Categories sorted alphabetically
- **WHEN** the SimulationView loads
- **THEN** the Category Select contains categories in alphabetical order (adversarial, baseline, compaction, dormancy, extraction, multi-session, trust)
