## ADDED Requirements

### Requirement: RunHistoryStore interface

A `RunHistoryStore` interface SHALL define the API contract for persisting simulation run records. The interface SHALL provide methods: `save(SimulationRunRecord)`, `load(String runId) -> Optional<SimulationRunRecord>`, `list() -> List<SimulationRunRecord>`, `listByScenario(String scenarioId) -> List<SimulationRunRecord>`, and `delete(String runId)`. All implementations SHALL be thread-safe.

#### Scenario: Save and load round-trip
- **WHEN** a SimulationRunRecord is saved via `save(record)`
- **THEN** `load(record.runId())` returns the same record with all fields preserved

#### Scenario: List returns all records
- **WHEN** 3 run records have been saved
- **THEN** `list()` returns all 3 records

#### Scenario: List by scenario filters correctly
- **WHEN** 2 runs used scenario "adversarial-contradictory" and 1 used "unit-drift"
- **THEN** `listByScenario("adversarial-contradictory")` returns exactly 2 records

#### Scenario: Delete removes record
- **WHEN** `delete(runId)` is called for an existing record
- **THEN** `load(runId)` returns `Optional.empty()`

### Requirement: SimulationRunStore

An `SimulationRunStore` SHALL implement `RunHistoryStore` using a `ConcurrentHashMap`. This SHALL be the default implementation. Data SHALL NOT survive application restarts. This implementation preserves current behavior.

#### Scenario: Data lost on restart
- **WHEN** run records are saved and the application restarts
- **THEN** `list()` returns an empty list

### Requirement: Neo4jRunHistoryStore

A `Neo4jRunHistoryStore` SHALL implement `RunHistoryStore` using Neo4j via Drivine. Each run record SHALL be stored as a `SimulationRun` node with properties: `runId` (String), `scenarioId` (String), `startedAt` (Instant), `completedAt` (Instant), `injectionEnabled` (boolean), and `payload` (String, JSON-serialized full record). The store SHALL use Jackson ObjectMapper to serialize/deserialize the full `SimulationRunRecord` to/from the `payload` property. Queryable properties (runId, scenarioId, timestamps) SHALL be stored as first-class node properties for indexing.

#### Scenario: Record persists across restarts
- **WHEN** a run record is saved and the application restarts
- **THEN** `load(runId)` returns the saved record with all fields intact

#### Scenario: JSON payload preserves all data
- **WHEN** a SimulationRunRecord with turn results, verdicts, and memory unit events is saved
- **THEN** the deserialized record contains all turn results, verdicts, and memory unit events

### Requirement: Store selection via configuration

The active `RunHistoryStore` implementation SHALL be selected via `arc-mem.run-history.store` configuration property. Valid values SHALL be `MEMORY` (SimulationRunStore) and `NEO4J` (Neo4jRunHistoryStore). The default SHALL be `NEO4J`.

#### Scenario: Neo4j store is default
- **WHEN** no `arc-mem.run-history.store` property is set
- **THEN** the `Neo4jRunHistoryStore` is used

#### Scenario: Memory store selected explicitly
- **WHEN** `arc-mem.run-history.store=MEMORY` is configured
- **THEN** the `SimulationRunStore` is used

## Added Requirements (initial-community-review-readiness)

### Requirement: RunHistoryStore interface (model identifier)

`RunHistoryStore` SHALL persist and retrieve a model identifier for each simulation run. The model identifier SHALL record which LLM was used for simulation turns so reports can surface provenance information.

#### Scenario: Run save includes model identifier
- **GIVEN** a completed simulation run
- **WHEN** the run is saved through `RunHistoryStore`
- **THEN** the model identifier used for LLM turns SHALL be persisted with the run record

#### Scenario: Run load returns persisted model identifier
- **GIVEN** a previously saved run
- **WHEN** the run is loaded by run ID
- **THEN** the loaded record SHALL include the model identifier

## Invariants (initial-community-review-readiness)

- **RHP1**: Run records used in resilience and benchmark reports SHALL include a model identifier.
