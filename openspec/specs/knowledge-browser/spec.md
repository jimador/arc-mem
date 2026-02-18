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

A `KnowledgeBrowserPanel` Vaadin component SHALL be available in the simulation review UI. The panel SHALL have three tabs: Propositions (all DICE propositions for the contextId with status and confidence filters), Anchors (active anchors with rank/authority sorting), and Graph (entity mention network showing which entities are connected through propositions). The panel SHALL load data from the AnchorBrowseController and DICE REST endpoints.

#### Scenario: Propositions tab displays all propositions
- **WHEN** the user selects the Propositions tab for a simulation context
- **THEN** all proposition nodes for that contextId are listed with their text, confidence, and status

#### Scenario: Anchors tab with sorting
- **WHEN** the user selects the Anchors tab and sorts by rank descending
- **THEN** anchors are displayed in rank-descending order with authority badges

#### Scenario: Graph tab shows entity network
- **WHEN** the user selects the Graph tab
- **THEN** an entity mention network visualization renders showing entities connected through shared propositions

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
