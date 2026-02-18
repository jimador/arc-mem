## ADDED Requirements

### Requirement: Entity mention network data derivation
The system SHALL derive a context-scoped entity mention network from proposition mentions in Neo4j. Nodes SHALL represent unique entities (resolved ID when present, otherwise normalized span+type fallback), and edges SHALL represent co-mentions within the same proposition. Edge weight SHALL equal the count of distinct propositions connecting the pair.

#### Scenario: Weighted co-mention edge is derived
- **GIVEN** two entities are mentioned together in three propositions in context `sim-abc123`
- **WHEN** the graph dataset is requested for `sim-abc123`
- **THEN** the returned edge between those entities has weight `3`

#### Scenario: Unresolved mention fallback identity is stable
- **GIVEN** a mention has no resolved ID and span/type values are present
- **WHEN** the graph dataset is built
- **THEN** the node identity SHALL use a deterministic normalized fallback key

### Requirement: Graph visualization controls and focus
The Graph tab SHALL provide interactive controls for at least minimum edge weight and entity type filtering. The visualization SHALL support focusing on a selected entity and SHALL render connected neighbors with visual emphasis while de-emphasizing unrelated nodes.

#### Scenario: Minimum edge weight filter reduces graph density
- **GIVEN** a graph dataset with edges of weights 1, 2, and 4
- **WHEN** the user sets minimum edge weight to 2
- **THEN** only edges with weights 2 and 4 are rendered

#### Scenario: Focusing an entity highlights its neighborhood
- **GIVEN** the graph is rendered for the current context
- **WHEN** the user selects an entity node
- **THEN** directly connected nodes and edges are highlighted
- **AND** non-neighbor nodes are visually de-emphasized

## MODIFIED Requirements

### Requirement: KnowledgeBrowserPanel with tabbed layout
A `KnowledgeBrowserPanel` Vaadin component SHALL be available in the simulation review UI. The panel SHALL have three tabs: Propositions (all DICE propositions for the contextId with status and confidence filters), Anchors (active anchors with rank/authority sorting), and Graph (interactive entity mention network visualization showing entities connected through shared propositions with weighted edges). The panel SHALL load data from repository-backed context-scoped queries and SHALL keep all tab behavior within the active simulation context.

#### Scenario: Propositions tab displays all propositions
- **GIVEN** a simulation context is active
- **WHEN** the user selects the Propositions tab for that context
- **THEN** all proposition nodes for that contextId are listed with text, confidence, and status

#### Scenario: Anchors tab with sorting
- **GIVEN** a simulation context is active
- **WHEN** the user selects the Anchors tab and sorts by rank descending
- **THEN** anchors are displayed in rank-descending order with authority badges

#### Scenario: Graph tab renders interactive network visualization
- **GIVEN** the context contains proposition mentions for at least two entities
- **WHEN** the user selects the Graph tab
- **THEN** an interactive entity mention network is rendered
- **AND** each edge indicates shared proposition connectivity between entity nodes

#### Scenario: Graph tab handles empty graph datasets gracefully
- **GIVEN** the selected context has no mention co-occurrence data
- **WHEN** the user opens the Graph tab
- **THEN** the UI shows a non-error empty state explaining no graph data is available

## Invariants

- Graph data SHALL be scoped to a single `contextId`.
- Graph rendering SHALL NOT mutate proposition or anchor persistence state.
- Visualization controls SHALL only affect graph presentation/filtering, not underlying data.
