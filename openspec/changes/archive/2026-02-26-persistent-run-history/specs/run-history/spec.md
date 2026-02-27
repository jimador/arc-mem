## MODIFIED Requirements

### Requirement: Store selection via configuration

The active `RunHistoryStore` implementation SHALL be selected via `dice-anchors.run-history.store` configuration property. Valid values SHALL be `MEMORY` (SimulationRunStore) and `NEO4J` (Neo4jRunHistoryStore). The default SHALL be `NEO4J`.

#### Scenario: Neo4j store is default
- **WHEN** no `dice-anchors.run-history.store` property is set
- **THEN** the `Neo4jRunHistoryStore` is used

#### Scenario: Memory store selected explicitly
- **WHEN** `dice-anchors.run-history.store=MEMORY` is configured
- **THEN** the `SimulationRunStore` is used
