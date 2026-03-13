# Context Unit LLM Tools Specification

## ADDED Requirements

### Requirement: Query facts tool

The LLM SHALL have access to a `queryFacts(subject)` tool that performs semantic search against active context units and returns matching results with rank, authority, and confidence.

#### Scenario: Query returns matching context units
- **WHEN** LLM calls queryFacts("Lich King")
- **THEN** semantic search returns context units whose text relates to "Lich King"
- **AND** results include id, text, rank, authority, pinned status, and confidence

#### Scenario: Query with no matches returns empty list
- **WHEN** LLM calls queryFacts("submarine") and no context units match
- **THEN** an empty list is returned

### Requirement: List context units tool

The LLM SHALL have access to a `listUnits()` tool that returns all active context units for the current context, ordered by rank descending.

#### Scenario: List returns active context units
- **WHEN** LLM calls listUnits()
- **THEN** all active context units for the chat context are returned
- **AND** results are ordered by rank descending
- **AND** each result includes id, text, rank, authority, pinned, confidence

### Requirement: Pin fact tool

The LLM SHALL have access to a `pinFact(unitId)` tool that pins an context unit, making it immune to budget eviction. The tool MUST NOT pin archived context units or modify CANON authority.

#### Scenario: Pin succeeds on active context unit
- **WHEN** LLM calls pinFact with a valid active context unit ID
- **THEN** the context unit is marked as pinned
- **AND** PinResult returns success=true with confirmation message

#### Scenario: Pin fails on non-existent context unit
- **WHEN** LLM calls pinFact with an invalid ID
- **THEN** PinResult returns success=false with error message

#### Scenario: Pin fails on archived context unit
- **WHEN** LLM calls pinFact with an archived context unit ID
- **THEN** PinResult returns success=false with explanation

### Requirement: Unpin fact tool

The LLM SHALL have access to an `unpinFact(unitId)` tool that unpins a previously pinned unit, restoring normal eviction eligibility.

#### Scenario: Unpin succeeds on pinned unit
- **WHEN** LLM calls unpinFact with a pinned unit ID
- **THEN** the context unit is unpinned
- **AND** PinResult returns success=true

#### Scenario: Unpin on already-unpinned context unit
- **WHEN** LLM calls unpinFact with an unpinned context unit ID
- **THEN** PinResult returns success=false with "not pinned" message

### Requirement: Tool safety guards

Tools MUST NOT allow operations that violate context unit invariants. No tool SHALL modify authority, assign CANON, change rank directly, or delete context units.

#### Scenario: No authority modification tools exist
- **WHEN** LLM attempts to change context unit authority via tools
- **THEN** no such tool is available

#### Scenario: Tool invocations are logged
- **WHEN** any context unit tool is called
- **THEN** the invocation is logged with tool name, parameters, and result

### Requirement: Tools wired into chat conversation

Context Unit tools SHALL be available during chat conversation via Embabel Agent's `@MatryoshkaTools` pattern. Tools SHALL use the chat contextId for all operations.

#### Scenario: Tools available during conversation
- **WHEN** LLM processes a user message in chat
- **THEN** context unit tools are available for the LLM to call
- **AND** tools operate on the chat context's context unit set

## Invariants

- **I1**: All tool operations MUST use the current chat contextId
- **I2**: Query tools MUST be read-only (no side effects)
- **I3**: Pin/unpin MUST NOT affect rank, authority, or reinforcement count
- **I4**: Tools MUST return records, not formatted strings
- **I5**: CANON context units MUST NOT be unpinned (they are inherently preserved)
