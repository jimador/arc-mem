# Anchor LLM Tools Specification

## ADDED Requirements

### Requirement: Query facts tool

The LLM SHALL have access to a `queryFacts(subject)` tool that performs semantic search against active anchors and returns matching results with rank, authority, and confidence.

#### Scenario: Query returns matching anchors
- **WHEN** LLM calls queryFacts("Lich King")
- **THEN** semantic search returns anchors whose text relates to "Lich King"
- **AND** results include id, text, rank, authority, pinned status, and confidence

#### Scenario: Query with no matches returns empty list
- **WHEN** LLM calls queryFacts("submarine") and no anchors match
- **THEN** an empty list is returned

### Requirement: List anchors tool

The LLM SHALL have access to a `listAnchors()` tool that returns all active anchors for the current context, ordered by rank descending.

#### Scenario: List returns active anchors
- **WHEN** LLM calls listAnchors()
- **THEN** all active anchors for the chat context are returned
- **AND** results are ordered by rank descending
- **AND** each result includes id, text, rank, authority, pinned, confidence

### Requirement: Pin fact tool

The LLM SHALL have access to a `pinFact(anchorId)` tool that pins an anchor, making it immune to budget eviction. The tool MUST NOT pin archived anchors or modify CANON authority.

#### Scenario: Pin succeeds on active anchor
- **WHEN** LLM calls pinFact with a valid active anchor ID
- **THEN** the anchor is marked as pinned
- **AND** PinResult returns success=true with confirmation message

#### Scenario: Pin fails on non-existent anchor
- **WHEN** LLM calls pinFact with an invalid ID
- **THEN** PinResult returns success=false with error message

#### Scenario: Pin fails on archived anchor
- **WHEN** LLM calls pinFact with an archived anchor ID
- **THEN** PinResult returns success=false with explanation

### Requirement: Unpin fact tool

The LLM SHALL have access to an `unpinFact(anchorId)` tool that unpins a previously pinned anchor, restoring normal eviction eligibility.

#### Scenario: Unpin succeeds on pinned anchor
- **WHEN** LLM calls unpinFact with a pinned anchor ID
- **THEN** the anchor is unpinned
- **AND** PinResult returns success=true

#### Scenario: Unpin on already-unpinned anchor
- **WHEN** LLM calls unpinFact with an unpinned anchor ID
- **THEN** PinResult returns success=false with "not pinned" message

### Requirement: Tool safety guards

Tools MUST NOT allow operations that violate anchor invariants. No tool SHALL modify authority, assign CANON, change rank directly, or delete anchors.

#### Scenario: No authority modification tools exist
- **WHEN** LLM attempts to change anchor authority via tools
- **THEN** no such tool is available

#### Scenario: Tool invocations are logged
- **WHEN** any anchor tool is called
- **THEN** the invocation is logged with tool name, parameters, and result

### Requirement: Tools wired into chat conversation

Anchor tools SHALL be available during chat conversation via Embabel Agent's `@MatryoshkaTools` pattern. Tools SHALL use the chat contextId for all operations.

#### Scenario: Tools available during conversation
- **WHEN** LLM processes a user message in chat
- **THEN** anchor tools are available for the LLM to call
- **AND** tools operate on the chat context's anchor set

## Invariants

- **I1**: All tool operations MUST use the current chat contextId
- **I2**: Query tools MUST be read-only (no side effects)
- **I3**: Pin/unpin MUST NOT affect rank, authority, or reinforcement count
- **I4**: Tools MUST return records, not formatted strings
- **I5**: CANON anchors MUST NOT be unpinned (they are inherently preserved)
