## MODIFIED Requirements

### Requirement: Silent failure elimination in duplicate detection

**Modifies**: `DuplicateDetector`.

Most catch blocks in `DuplicateDetector` already log at WARN level with fallback behavior. This requirement ensures **all** catch blocks follow the same pattern consistently. Specifically, any catch block that returns a permissive default SHALL log at WARN level with:
- The operation that failed (e.g., "LLM duplicate check")
- The exception type and message
- The fallback value being returned (e.g., "returning false (unique)")
- The contextId and candidate text (for correlation)

The permissive default behavior SHALL be preserved (transient failures should not block promotions), but failures MUST be observable in logs. Review all catch blocks and upgrade any logging below WARN to WARN level.

#### Scenario: LLM duplicate check failure logged

- **GIVEN** the LLM-based duplicate detector encounters a timeout or API error
- **WHEN** `isDuplicate()` is called for candidate text "The sky is blue"
- **THEN** a WARN log is emitted: "LLM duplicate check failed for candidate 'The sky is blue' in context 'ctx-1': TimeoutException - Connection timed out. Returning false (unique)."
- **AND** `false` (not a duplicate) is returned

#### Scenario: Batch LLM duplicate check failure logged

- **GIVEN** the LLM-based batch duplicate detector encounters an error
- **WHEN** `batchIsDuplicate()` is called for 5 candidates
- **THEN** a WARN log is emitted with the error details and all 5 candidates are marked as unique (fallback)

### Requirement: Silent failure elimination in conflict detection

**Modifies**: `LlmConflictDetector`.

The `LlmConflictDetector.parseResponse()` method currently logs JSON parse failures at DEBUG level — this SHALL be upgraded to WARN. All other catch blocks in `LlmConflictDetector` already log at WARN level with appropriate fallback behavior.

Specifically, `parseResponse()` SHALL log at WARN level when JSON parsing fails, including: the raw response text (truncated to 200 chars), the exception type and message, and the fallback behavior applied (e.g., "falling back to text-contains-true heuristic").

#### Scenario: LLM conflict detection failure logged

- **GIVEN** the LLM-based conflict detector encounters an API error
- **WHEN** `detect()` is called for incoming text against existing context units
- **THEN** a WARN log is emitted with the error details
- **AND** an empty conflict list is returned (safe default)

#### Scenario: Batch LLM conflict detection failure logged

- **GIVEN** the LLM-based batch conflict detector encounters an error
- **WHEN** `batchDetect()` is called
- **THEN** a WARN log is emitted and all candidates report no conflicts (safe fallback)

### Requirement: Intra-batch deduplication against existing context units

**Modifies**: `UnitPromoter` promotion pipeline.

The duplicate detection gate in the promotion pipeline SHALL check incoming candidates against both:
1. Other candidates in the same batch (intra-batch dedup, existing behavior)
2. Active context units in the current context (cross-reference dedup, new)

Currently, batch dedup only filters within the incoming batch by text identity. The cross-reference against existing context units prevents promoting a proposition that duplicates an context unit already in the context.

#### Scenario: Candidate duplicates existing context unit filtered

- **GIVEN** an active context unit with text "Water boils at 100°C" exists in context "ctx-1"
- **WHEN** a proposition with text "Water boils at 100 degrees Celsius" enters the promotion pipeline
- **THEN** the duplicate detector identifies it as a duplicate of the existing context unit and the proposition is filtered out

#### Scenario: Candidate unique across batch and existing context units promoted

- **GIVEN** active context units exist but none match the incoming proposition
- **WHEN** a unique proposition enters the promotion pipeline
- **THEN** the proposition passes the duplicate detection gate

### Requirement: Promotion pipeline documentation

**Modifies**: `UnitPromoter` class-level Javadoc.

`UnitPromoter` SHALL have comprehensive class-level Javadoc documenting:
- The complete pipeline as a cohesive flow: confidence gate → duplicate detection → conflict detection + resolution → trust evaluation → promotion
- Each gate's purpose and what it filters
- What happens at each stage (pass-through, filtered, or transformed)
- How batch processing works (candidates processed as a group through each gate)
- The relationship between the promoter and `ArcMemEngine` (promoter decides what to promote, engine executes the promotion)

### Requirement: ContextTools documentation and demote tool

**Modifies**: `ContextTools` (@MatryoshkaTools).

Each `@LlmTool` method in `ContextTools` SHALL have a comprehensive `@LlmTool(description=...)` that clearly documents:
- What the tool does
- What parameters it accepts
- What guardrails apply (e.g., "cannot modify authority")
- When the LLM should and should not use the tool

The system SHALL add a `demoteUnit(String unitId, String reason)` tool that allows the LLM to request demotion of an context unit's authority by one level. The tool SHALL:
- Call `ArcMemEngine.demote()` with `DemotionReason.MANUAL`
- Return a result indicating the new authority level
- For CANON context units: create a pending decanonization request via `CanonizationGate` instead of immediate demotion

#### Scenario: LLM demotes RELIABLE context unit

- **GIVEN** the LLM identifies context unit "A1" at RELIABLE authority as potentially outdated
- **WHEN** the LLM calls `demoteUnit("A1", "Evidence suggests this fact is outdated")`
- **THEN** the context unit's authority is demoted to UNRELIABLE

#### Scenario: LLM attempts to demote CANON context unit

- **GIVEN** context unit "A1" is at CANON authority
- **WHEN** the LLM calls `demoteUnit("A1", "Conflicting information found")`
- **THEN** a pending decanonization request is created (not immediate demotion) and the tool returns a message indicating human approval is required

#### Scenario: LLM demotes non-existent context unit

- **GIVEN** no context unit exists with ID "missing"
- **WHEN** the LLM calls `demoteUnit("missing", "reason")`
- **THEN** the tool returns an error message indicating the context unit was not found
