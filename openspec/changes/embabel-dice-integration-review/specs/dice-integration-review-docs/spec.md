## MODIFIED Requirements

### Requirement: DICE integration surface documentation

The repository SHALL include a canonical DICE integration surface document under `docs/dev/dice-integration.md` that explains how Anchors composes with DICE proposition extraction and lifecycle behavior.

#### Scenario: Integration architecture is explicit
- **WHEN** the DICE integration document is reviewed
- **THEN** it SHALL describe proposition extraction, revision, persistence, and promotion as an end-to-end flow
- **AND** SHALL link code paths (file:line references) for each integration stage

#### Scenario: Component API usage is documented
- **WHEN** component usage section is reviewed
- **THEN** it SHALL describe each component: `LlmPropositionExtractor`, `LlmPropositionReviser`, `PropositionPipeline`, `PropositionIncrementalAnalyzer`
- **AND** SHALL document current 0.1.0-SNAPSHOT APIs used (with specific method signatures where applicable)

#### Scenario: Fragile coupling points are identified and monitored
- **WHEN** fragile coupling section is reviewed
- **THEN** it SHALL document: `PropositionView.toDice()` using 13-param `Proposition.create()` overload
- **AND** SHALL document: `DiceAnchorsChunkHistoryStore` delegation wrapper around `ChunkHistoryStore` interface
- **AND** SHALL document: `PropositionIncrementalAnalyzer` windowed analysis with configurable parameters
- **AND** SHALL state monitoring strategy: monitor SNAPSHOT releases, add integration tests for API compatibility

### Requirement: DICE vs. Anchors responsibility boundaries

The documentation SHALL clearly distinguish which behaviors are implemented in dice-anchors vs. expected from upstream DICE components.

#### Scenario: Local vs. upstream behavior is unambiguous
- **WHEN** a developer reads the integration document
- **THEN** it SHALL state what dice-anchors implements (Anchor lifecycle, trust pipeline, conflict resolution)
- **AND** SHALL state what comes from DICE (proposition extraction, incremental analysis, chunk history)
- **AND** SHALL clarify extension points and contracts
