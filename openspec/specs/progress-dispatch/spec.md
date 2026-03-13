## ADDED Requirements

### Requirement: SimulationProgressListener interface
A `SimulationProgressListener` interface MUST be defined with three default methods: `onTurnStarted`, `onTurnCompleted`, and `onSimulationCompleted`. Each method MUST accept a `SimulationProgress` parameter and default to no-op.

#### Scenario: Panel implements only onTurnCompleted
- **WHEN** a panel only cares about completed turns
- **THEN** it MUST be able to implement only `onTurnCompleted` without overriding other methods

#### Scenario: Panel implements all three methods
- **WHEN** a panel needs to handle pre-turn, post-turn, and completion events
- **THEN** it MUST be able to override all three methods independently

### Requirement: ProgressDispatcher utility
A `ProgressDispatcher` class MUST manage listener registration and dispatch progress events to all registered listeners. It MUST NOT be a Spring bean — it is a UI-scoped utility owned by `SimulationView`.

#### Scenario: Dispatching a pre-turn event
- **WHEN** `dispatch()` is called with a progress where `lastPlayerMessage != null` and `lastDmResponse == null`
- **THEN** all registered listeners MUST receive an `onTurnStarted` call

#### Scenario: Dispatching a post-turn event
- **WHEN** `dispatch()` is called with a progress where `lastDmResponse != null` and `complete == false`
- **THEN** all registered listeners MUST receive an `onTurnCompleted` call

#### Scenario: Dispatching a completion event
- **WHEN** `dispatch()` is called with a progress where `complete == true`
- **THEN** all registered listeners MUST receive an `onSimulationCompleted` call

#### Scenario: Listener registration order preserved
- **WHEN** multiple listeners are registered
- **THEN** they MUST be notified in registration order

### Requirement: SimulationView delegates to ProgressDispatcher
`SimulationView.applyProgress()` MUST delegate panel-specific updates to the `ProgressDispatcher`. SimulationView MUST retain only view-level concerns: progress bar, status label, intervention banner dismissal, and state machine transitions.

#### Scenario: applyProgress after refactor
- **WHEN** `applyProgress()` is called with a mid-simulation progress event
- **THEN** SimulationView MUST update the progress bar and status label, then call `dispatcher.dispatch(progress)`
- **THEN** SimulationView MUST NOT directly call panel-specific methods like `conversationPanel.appendTurn()` or `inspectorPanel.update()`

#### Scenario: Terminal state handling
- **WHEN** `applyProgress()` is called with `progress.complete() == true`
- **THEN** SimulationView MUST call `dispatcher.dispatch(progress)` and then `transitionTo(SimControlState.COMPLETED)`

### Requirement: Panels implement SimulationProgressListener
Each panel that currently receives updates from `applyProgress()` MUST implement `SimulationProgressListener` and handle its own updates internally.

#### Scenario: ConversationPanel handles turn lifecycle
- **WHEN** `onTurnStarted` is called
- **THEN** ConversationPanel MUST append the player bubble and show the thinking indicator
- **WHEN** `onTurnCompleted` is called
- **THEN** ConversationPanel MUST remove the thinking indicator and append the DM bubble

#### Scenario: DriftSummaryPanel handles verdicts
- **WHEN** `onTurnCompleted` is called with non-null verdicts
- **THEN** DriftSummaryPanel MUST call `recordTurnVerdicts` internally

#### Scenario: ContextInspectorPanel handles context trace
- **WHEN** `onTurnCompleted` is called with a non-null context trace
- **THEN** ContextInspectorPanel MUST update its memory-unit/verdict/prompt displays

#### Scenario: UnitTimelinePanel handles turn and events
- **WHEN** `onTurnCompleted` is called
- **THEN** UnitTimelinePanel MUST call `appendTurn` and `appendUnitEvents` internally
