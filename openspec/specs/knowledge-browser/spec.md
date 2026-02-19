## ADDED Requirements

### Requirement: DICE REST endpoint activation

`DiceAnchorsApplication` SHALL activate DICE REST endpoints via `@Import(DiceRestConfiguration.class)`. This SHALL enable the `MemoryController` (list/get/search/delete propositions by contextId) and `PropositionPipelineController` (extract from text/file) out of the box, with no additional configuration required.

#### Scenario: Memory endpoints available
- **WHEN** the application starts with the DICE REST import
- **THEN** `GET /memory/{contextId}` returns propositions for the given context
- **AND** `POST /memory/search` accepts a text query and returns similar propositions

#### Scenario: Pipeline endpoints available
- **WHEN** the application starts with the DICE REST import
- **THEN** `POST /propositions/extract` accepts text input and returns extracted propositions

### Requirement: AnchorBrowseController REST endpoints

An `AnchorBrowseController` SHALL provide REST endpoints extending DICE's proposition browsing with anchor-specific fields. The controller SHALL expose: `GET /anchors/{contextId}` (list active anchors with rank, authority, pinned, decayType, reinforcementCount), `GET /anchors/{contextId}/{id}` (get anchor by ID), `GET /anchors/{contextId}/search?authority={level}` (search by authority level), and `GET /anchors/{contextId}/{id}/history` (get anchor reinforcement/decay event history).

#### Scenario: List active anchors
- **WHEN** `GET /anchors/sim-abc123` is called
- **THEN** the response contains all active anchors for that contextId with rank, authority, pinned, decayType, and reinforcementCount fields

#### Scenario: Filter by authority
- **WHEN** `GET /anchors/sim-abc123/search?authority=RELIABLE` is called
- **THEN** only anchors with RELIABLE authority are returned

#### Scenario: Get anchor history
- **WHEN** `GET /anchors/sim-abc123/{id}/history` is called for an anchor that has been reinforced 3 times
- **THEN** the response includes the reinforcement event history

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

### Requirement: Anchor-specific filters

The Anchors tab of the KnowledgeBrowserPanel SHALL provide filter controls for: authority level (combo box with PROVISIONAL, UNRELIABLE, RELIABLE, CANON), rank range (min/max sliders or number fields for 100-900), pinned status (checkbox), and decay type (combo box). Filters SHALL be applied client-side or via parameterized REST calls. Multiple filters SHALL be composable (AND logic).

#### Scenario: Filter by authority and rank range
- **WHEN** the user selects authority=RELIABLE and rank range 400-700
- **THEN** only anchors matching both criteria are displayed

#### Scenario: Filter by pinned status
- **WHEN** the user checks the "Pinned only" filter
- **THEN** only pinned anchors are displayed

### Requirement: Semantic search integration

The KnowledgeBrowserPanel SHALL include a text search field that delegates to DICE's `POST /memory/search` endpoint for vector similarity search. Search results SHALL display matched propositions and anchors ranked by similarity score. The search field SHALL have a minimum query length of 3 characters before triggering a search.

#### Scenario: Semantic search returns results
- **WHEN** the user types "dragon breath fire" in the search field
- **THEN** propositions semantically similar to the query are displayed with similarity scores

#### Scenario: Short query ignored
- **WHEN** the user types "ab" (2 characters)
- **THEN** no search is triggered and a hint message indicates minimum length

### Requirement: Link from ContextInspectorPanel to KnowledgeBrowserPanel

Anchor cards in the ContextInspectorPanel SHALL include a clickable link or button that navigates to the KnowledgeBrowserPanel filtered to that specific anchor. Clicking the link SHALL open the KnowledgeBrowserPanel (or navigate to it) with the anchor's ID pre-selected, showing the anchor's full proposition history, mentions, and graph neighborhood.

#### Scenario: Click anchor navigates to browser
- **WHEN** the user clicks the "Browse" link on an anchor card in the ContextInspectorPanel
- **THEN** the KnowledgeBrowserPanel opens filtered to that anchor's ID
- **AND** the anchor's full details, history, and graph neighborhood are displayed

### Requirement: Context-scoped browsing

All data displayed in the KnowledgeBrowserPanel SHALL be scoped to the current simulation contextId. The panel SHALL not show propositions or anchors from other simulation runs or the chat context. The contextId SHALL be passed to all REST endpoint calls.

#### Scenario: Only current context data shown
- **WHEN** the browser panel loads for simulation context "sim-abc123"
- **THEN** only propositions and anchors with contextId "sim-abc123" are displayed
- **AND** data from other contexts is not visible

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

## Invariants

- Graph data SHALL be scoped to a single `contextId`.
- Graph rendering SHALL NOT mutate proposition or anchor persistence state.
- Visualization controls SHALL only affect graph presentation/filtering, not underlying data.
