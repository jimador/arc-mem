## ADDED Requirements

### Requirement: Condition checkbox selection

The `ExperimentConfigPanel` SHALL render exactly four condition checkboxes: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY`, and `NO_RANK_DIFFERENTIATION`. Each checkbox SHALL correspond to a distinct `AblationCondition` enum value. At least two conditions MUST be selected before the experiment may be started.

#### Scenario: All four conditions rendered

- **WHEN** the `ExperimentConfigPanel` is rendered
- **THEN** checkboxes for `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY`, and `NO_RANK_DIFFERENTIATION` SHALL each be visible and individually toggleable

#### Scenario: Single condition selected — run button remains disabled

- **GIVEN** only `FULL_ANCHORS` is checked
- **WHEN** the user views the "Run Experiment" button
- **THEN** the button SHALL be disabled

#### Scenario: Two conditions selected — run button becomes enabled

- **GIVEN** `FULL_ANCHORS` and `NO_ANCHORS` are both checked and at least one scenario is selected
- **WHEN** the user views the "Run Experiment" button
- **THEN** the button SHALL be enabled

#### Scenario: Deselecting below threshold disables button

- **GIVEN** three conditions are selected and one scenario is selected
- **WHEN** the user unchecks two of the three condition checkboxes
- **THEN** the "Run Experiment" button SHALL become disabled

### Requirement: Scenario multi-select with category tags

The `ExperimentConfigPanel` SHALL render a multi-select component populated from `ScenarioLoader`. Each selectable scenario SHALL display its name alongside a category tag indicating `adversarial`, `baseline`, or `feature-specific`. At least one scenario MUST be selected before the experiment may be started.

#### Scenario: Scenarios populated from ScenarioLoader

- **GIVEN** `ScenarioLoader` returns three scenarios: one adversarial, one baseline, one feature-specific
- **WHEN** the `ExperimentConfigPanel` renders the scenario selector
- **THEN** all three scenarios SHALL be listed with their respective category tags visible

#### Scenario: Category tags are displayed inline

- **GIVEN** a scenario named `"adversarial-contradictory"` with category `adversarial`
- **WHEN** the user views the scenario selector
- **THEN** the entry SHALL display both the name `"adversarial-contradictory"` and the tag `"adversarial"`

#### Scenario: No scenario selected — run button disabled

- **GIVEN** two conditions are selected but no scenario is selected
- **WHEN** the user views the "Run Experiment" button
- **THEN** the button SHALL be disabled

#### Scenario: One scenario selected satisfies the requirement

- **GIVEN** two conditions are selected and one scenario is selected
- **WHEN** the user views the "Run Experiment" button
- **THEN** the button SHALL be enabled

### Requirement: Repetition slider with range enforcement

The `ExperimentConfigPanel` SHALL render a repetition count slider with minimum value 2, maximum value 20, and default value 5. The slider SHALL clamp any programmatic or user-supplied value to the range [2, 20].

#### Scenario: Default value on first render

- **GIVEN** the `ExperimentConfigPanel` is rendered without prior interaction
- **WHEN** the user views the repetition slider
- **THEN** the slider SHALL display the value 5

#### Scenario: Minimum boundary enforced

- **GIVEN** the repetition slider
- **WHEN** the user attempts to set the value to 1
- **THEN** the slider SHALL clamp to 2 and display 2

#### Scenario: Maximum boundary enforced

- **GIVEN** the repetition slider
- **WHEN** the user attempts to set the value to 25
- **THEN** the slider SHALL clamp to 20 and display 20

#### Scenario: Mid-range value accepted

- **GIVEN** the repetition slider
- **WHEN** the user sets the value to 10
- **THEN** the slider SHALL display 10 and the value 10 SHALL be passed to `ExperimentRunner` on start

### Requirement: Optional evaluator model override

The `ExperimentConfigPanel` SHOULD render an optional text field for overriding the evaluator model used during scoring. When the field is left empty, each scenario's configured evaluator model SHALL be used. When a value is provided, it SHALL be forwarded to the `ExperimentRunner` as the evaluator model override for all cells.

#### Scenario: Empty field uses scenario default

- **GIVEN** the evaluator model override field is empty
- **WHEN** the experiment starts
- **THEN** each cell SHALL use the evaluator model specified in the scenario definition

#### Scenario: Non-empty field overrides for all cells

- **GIVEN** the evaluator model override field contains `"gpt-4o"`
- **WHEN** the experiment starts
- **THEN** every cell in the experiment SHALL use `"gpt-4o"` as the evaluator model

### Requirement: Run Experiment button gating

The "Run Experiment" button MUST be disabled whenever the selection state is invalid. A valid selection requires at least 2 conditions checked AND at least 1 scenario selected. The button SHALL become enabled immediately when both thresholds are met and SHALL become disabled immediately when either threshold is violated.

#### Scenario: Button enabled only on valid selection

- **GIVEN** two conditions checked and two scenarios selected
- **WHEN** the user views the "Run Experiment" button
- **THEN** the button SHALL be enabled and clickable

#### Scenario: Button disabled when conditions drop below threshold

- **GIVEN** the button is currently enabled (2 conditions, 1 scenario)
- **WHEN** the user unchecks one condition checkbox
- **THEN** the button SHALL become disabled without requiring any other user action

#### Scenario: Button disabled when all scenarios deselected

- **GIVEN** the button is currently enabled (3 conditions, 1 scenario)
- **WHEN** the user deselects the only selected scenario
- **THEN** the button SHALL become disabled without requiring any other user action

#### Scenario: Clicking enabled button starts experiment

- **GIVEN** the "Run Experiment" button is enabled
- **WHEN** the user clicks the button
- **THEN** `ExperimentRunner.runExperiment()` SHALL be invoked with the configured conditions, scenarios, and repetition count

## Invariants

- **EX-CFG1**: The "Run Experiment" button MUST be disabled when fewer than 2 conditions are checked OR when no scenario is selected. This invariant is evaluated reactively on every change to the condition checkboxes or scenario selection.
- **EX-CFG2**: The repetition slider MUST clamp its value to [2, 20] at all times. Values outside this range SHALL NOT be passed to `ExperimentRunner`.
