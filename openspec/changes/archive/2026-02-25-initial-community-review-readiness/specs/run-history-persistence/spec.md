## MODIFIED Requirements

### Requirement: RunHistoryStore interface

`RunHistoryStore` SHALL persist and retrieve a model identifier for each simulation run. The model identifier SHALL record which LLM was used for simulation turns so reports can surface provenance information.

#### Scenario: Run save includes model identifier
- **GIVEN** a completed simulation run
- **WHEN** the run is saved through `RunHistoryStore`
- **THEN** the model identifier used for LLM turns SHALL be persisted with the run record

#### Scenario: Run load returns persisted model identifier
- **GIVEN** a previously saved run
- **WHEN** the run is loaded by run ID
- **THEN** the loaded record SHALL include the model identifier

## Invariants

- **RHP1**: Run records used in resilience and benchmark reports SHALL include a model identifier.
