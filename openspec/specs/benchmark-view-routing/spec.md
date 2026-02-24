## ADDED Requirements

### Requirement: Dedicated /benchmark Vaadin route

The system SHALL provide a `BenchmarkView` class in the `sim.views` package annotated with `@Route("benchmark")`. `BenchmarkView` SHALL extend `VerticalLayout`, following the same pattern as `SimulationView` (`@Route("")`) and `ChatView` (`@Route("chat")`). The route SHALL be accessible at `/benchmark` in the running application without authentication.

#### Scenario: BenchmarkView accessible at /benchmark

- **GIVEN** the application is running
- **WHEN** the user navigates to `/benchmark`
- **THEN** the `BenchmarkView` SHALL render and no 404 or redirect SHALL occur

#### Scenario: Route does not conflict with root or chat routes

- **GIVEN** the `@Route("benchmark")` annotation is registered
- **WHEN** the user navigates to `/`
- **THEN** `SimulationView` SHALL render (not `BenchmarkView`)

#### Scenario: Route does not conflict with /chat

- **GIVEN** the `@Route("benchmark")` annotation is registered
- **WHEN** the user navigates to `/chat`
- **THEN** `ChatView` SHALL render (not `BenchmarkView`)

### Requirement: Navigation link from SimulationView to BenchmarkView

`SimulationView` SHALL include a navigation link labeled `"Benchmark"` in its header area. Clicking the link SHALL navigate the user to `/benchmark`.

#### Scenario: Benchmark link visible in SimulationView header

- **GIVEN** the user is on the SimulationView (`/`)
- **WHEN** the user views the header
- **THEN** a `"Benchmark"` navigation link SHALL be visible

#### Scenario: Benchmark link navigates to /benchmark

- **GIVEN** the user is on SimulationView
- **WHEN** the user clicks the `"Benchmark"` link
- **THEN** the browser SHALL navigate to `/benchmark` and `BenchmarkView` SHALL render

### Requirement: Navigation link from ChatView to BenchmarkView

`ChatView` SHALL include a navigation link labeled `"Benchmark"` in its header area. Clicking the link SHALL navigate the user to `/benchmark`.

#### Scenario: Benchmark link visible in ChatView header

- **GIVEN** the user is on the ChatView (`/chat`)
- **WHEN** the user views the header
- **THEN** a `"Benchmark"` navigation link SHALL be visible

#### Scenario: Benchmark link from ChatView navigates correctly

- **GIVEN** the user is on ChatView
- **WHEN** the user clicks the `"Benchmark"` link
- **THEN** the browser SHALL navigate to `/benchmark` and `BenchmarkView` SHALL render

### Requirement: Navigation links from BenchmarkView to SimulationView and ChatView

`BenchmarkView` SHALL include navigation links labeled `"Simulator"` and `"Chat"` in its header area. Clicking `"Simulator"` SHALL navigate to `/`. Clicking `"Chat"` SHALL navigate to `/chat`.

#### Scenario: Simulator and Chat links visible in BenchmarkView header

- **GIVEN** the user is on BenchmarkView (`/benchmark`)
- **WHEN** the user views the header
- **THEN** both `"Simulator"` and `"Chat"` navigation links SHALL be visible

#### Scenario: Simulator link navigates to root

- **GIVEN** the user is on BenchmarkView
- **WHEN** the user clicks `"Simulator"`
- **THEN** the browser SHALL navigate to `/` and `SimulationView` SHALL render

#### Scenario: Chat link navigates to /chat

- **GIVEN** the user is on BenchmarkView
- **WHEN** the user clicks `"Chat"`
- **THEN** the browser SHALL navigate to `/chat` and `ChatView` SHALL render

### Requirement: BenchmarkView layout structure

`BenchmarkView` SHALL consist of a header area containing the view title and navigation links, and a content area that hosts the active panel. The content area SHALL display one panel at a time, determined by the current experiment lifecycle state: `ExperimentConfigPanel` when no experiment is running and no results are loaded, `ExperimentProgressPanel` while an experiment is executing, and `ConditionComparisonPanel` when results are available (either from a completed or cancelled experiment, or from a loaded history entry). The `ExperimentHistoryPanel` SHALL be accessible as a persistent sidebar or a dedicated section within the layout.

#### Scenario: Config panel shown on initial load

- **GIVEN** no experiment is in progress and no results are loaded
- **WHEN** the user navigates to `/benchmark`
- **THEN** the `ExperimentConfigPanel` SHALL be displayed in the content area

#### Scenario: Progress panel shown during execution

- **GIVEN** the user has started an experiment from the config panel
- **WHEN** the experiment begins executing
- **THEN** the content area SHALL transition to show the `ExperimentProgressPanel`

#### Scenario: Comparison panel shown on completion

- **GIVEN** an experiment has completed (or been cancelled with partial results)
- **WHEN** execution finishes
- **THEN** the content area SHALL transition to show the `ConditionComparisonPanel`

#### Scenario: Comparison panel shown on history load

- **GIVEN** the user selects a past experiment from the `ExperimentHistoryPanel`
- **WHEN** the load completes
- **THEN** the content area SHALL show the `ConditionComparisonPanel` populated with the loaded experiment's data

### Requirement: anchor-retro CSS theme compliance

All new CSS classes introduced by `BenchmarkView` and its sub-panels SHALL follow the `ar-bench-*` naming convention established in `frontend/themes/anchor-retro/styles.css`. No external charting or styling libraries SHALL be introduced unless Vaadin's built-in components are demonstrably insufficient for the required visualization.

#### Scenario: New CSS classes use ar-bench- prefix

- **GIVEN** the `BenchmarkView` applies CSS classes to comparison cards, delta badges, heatmap cells, and effect size indicators
- **WHEN** those classes are inspected in the stylesheet
- **THEN** every new class SHALL begin with `ar-bench-`

#### Scenario: No external styling library dependency introduced

- **GIVEN** the `BenchmarkView` build configuration
- **WHEN** the project compiles
- **THEN** no new external CSS or JavaScript charting library SHALL be added as a Maven or npm dependency solely for `BenchmarkView`

### Requirement: Constructor injection for BenchmarkView and sub-panels

`BenchmarkView` and all sub-panel classes (`ExperimentConfigPanel`, `ExperimentProgressPanel`, `ConditionComparisonPanel`, `FactDrillDownPanel`, `ExperimentHistoryPanel`) SHALL use constructor injection for all Spring-managed dependencies. Field-level `@Autowired` SHALL NOT be used in any of these classes.

#### Scenario: BenchmarkView uses constructor injection

- **GIVEN** `BenchmarkView` depends on `ScenarioLoader`, `ExperimentRunner`, and `RunHistoryStore`
- **WHEN** the class is instantiated by the Spring container
- **THEN** all dependencies SHALL be injected via the constructor, not via field annotation

## Invariants

- **BVR1**: The `@Route("benchmark")` registration MUST NOT interfere with the existing `@Route("")` (SimulationView) or `@Route("chat")` (ChatView) routes. All three routes SHALL coexist and each SHALL render the correct view independently.
- **BVR2**: Navigation between all three views MUST be bidirectional. Any view MUST be reachable from any other view in at most one click using the header navigation links. The navigation graph MUST form a complete triangle: SimulationView -> BenchmarkView, BenchmarkView -> SimulationView, ChatView -> BenchmarkView, BenchmarkView -> ChatView, SimulationView -> ChatView (existing), ChatView -> SimulationView (existing).
