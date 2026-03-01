## MODIFIED Requirements

### Requirement: Tools organized by concern (query vs. mutation)

Anchor tools SHALL be organized into two separate `@MatryoshkaTools` classes following Command Query Segregation (CQS): one for read operations, one for write operations. This separation enables selective tool registration based on context (read-only simulation vs. full-access chat).

#### Scenario: Query tools always available
- **WHEN** an anchor tool session is initialized
- **THEN** `AnchorQueryTools` (@MatryoshkaTools) is registered
- **AND** includes: `queryFacts(subject)`, `listAnchors()`, `retrieveAnchors(query)`

#### Scenario: Mutation tools context-conditional
- **WHEN** a chat session is initialized
- **THEN** both `AnchorQueryTools` and `AnchorMutationTools` are registered

#### Scenario: Read-only contexts skip mutation tools
- **WHEN** a read-only context is configured (e.g., simulation, audit mode)
- **THEN** only `AnchorQueryTools` is registered
- **AND** `AnchorMutationTools` is not available to the LLM

### Requirement: AnchorQueryTools class

`AnchorQueryTools` is a `@MatryoshkaTools` class with three `@LlmTool` methods exposing all read operations on anchors.

#### Scenario: queryFacts performs semantic search
- **WHEN** LLM calls `queryFacts(subject)`
- **THEN** the tool returns up to 10 anchors matching subject via semantic search
- **AND** results include: id, text, rank, authority, pinned status, confidence

#### Scenario: listAnchors returns all active anchors
- **WHEN** LLM calls `listAnchors()`
- **THEN** all active anchors for the context are returned, ordered by rank descending
- **AND** each result includes: id, text, rank, authority, pinned, confidence

#### Scenario: retrieveAnchors blends LLM semantics with heuristics
- **WHEN** LLM calls `retrieveAnchors(query)`
- **THEN** the tool uses `RelevanceScorer` to blend LLM semantic scores with heuristic signals (authority, tier, confidence)
- **AND** applies minRelevance quality gate and top-k limit
- **AND** tracks tool call count via OTEL span attributes
- **AND** conditional: only available when retrieval mode is HYBRID or TOOL (not CONTEXT_ONLY)

### Requirement: AnchorMutationTools class

`AnchorMutationTools` is a `@MatryoshkaTools` class with three `@LlmTool` methods exposing safe mutations on anchors.

#### Scenario: pinFact marks anchor as eviction-immune
- **WHEN** LLM calls `pinFact(anchorId)`
- **THEN** the anchor is marked pinned, preventing budget eviction
- **AND** returns success=true with confirmation
- **AND** returns success=false with reason if anchor not found, not an anchor, or archived

#### Scenario: unpinFact restores normal eviction eligibility
- **WHEN** LLM calls `unpinFact(anchorId)`
- **THEN** the anchor is unpinned
- **AND** returns success=true with confirmation, or success=false if not pinned

#### Scenario: demoteAnchor lowers authority
- **WHEN** LLM calls `demoteAnchor(anchorId)`
- **THEN** the anchor authority is demoted (CANON → RELIABLE → UNRELIABLE → PROVISIONAL)
- **AND** returns success=true, or success=false if anchor cannot be demoted (already PROVISIONAL, or CANON with pending gate request)

#### Scenario: Mutation tools enforce anchor invariants
- **WHEN** any mutation tool executes
- **THEN** rank bounds [100, 900], CANON immutability, and authority rules SHALL be enforced
- **AND** tool invocations are logged with parameters and result
