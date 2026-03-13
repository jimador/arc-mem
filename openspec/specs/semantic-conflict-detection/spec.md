# Semantic Conflict Detection Specification

## ADDED Requirements

### Requirement: Semantic conflict detection via LLM

The system SHALL detect semantic opposition between an incoming statement and existing memory units via LLM-based analysis. Semantic opposition includes word-level negation ("alive" vs "dead"), domain opposition ("supports" vs "opposes"), and conceptual contradiction on the same subject.

#### Scenario: Semantic opposition detection
- **WHEN** incoming "Mars supports life" is checked against existing memory unit "Mars cannot support life"
- **THEN** semantic detector identifies opposition beyond simple negation markers
- **AND** returns a Conflict with reason "semantic opposition: supports vs cannot support"

#### Scenario: Same-subject semantic contradiction
- **WHEN** incoming "The Earth rotates clockwise" is checked against "The Earth rotates counterclockwise"
- **THEN** semantic detector identifies opposition on same subject (Earth rotation)
- **AND** returns conflict

#### Scenario: Different-subject rejection
- **WHEN** incoming "Mars is red" is checked against "Water is essential"
- **THEN** semantic detector does not identify opposition (disjoint subjects)
- **AND** returns empty conflict list

### Requirement: Subject filtering for semantic detection efficiency

The conflict detection system SHALL apply subject filtering before invoking semantic detection. Only candidates with shared subjects (named entities, domain nouns, explicit topic markers) SHALL be checked via LLM.

#### Scenario: Subject overlap triggers semantic check
- **WHEN** incoming "Mars is habitable" shares subject "Mars" with existing memory unit
- **THEN** subject filter identifies overlap
- **AND** semantic detector is invoked for LLM check

#### Scenario: No subject overlap skips semantic check
- **WHEN** incoming "The sun is bright" has no shared subjects with existing memory units
- **THEN** subject filter excludes it from semantic check
- **AND** LLM is not invoked

#### Scenario: Multiple shared subjects
- **WHEN** incoming "Einstein's theory of relativity" shares "Einstein" and "theory" with existing memory units
- **THEN** subject filter identifies multiple overlaps
- **AND** semantic check is performed

### Requirement: Configurable conflict detection strategy

The system SHALL support configurable conflict detection strategy via property `unit.conflict-detection-strategy`. The property MUST accept three values:
- LEXICAL_ONLY: NegationConflictDetector only (current behavior)
- LEXICAL_THEN_SEMANTIC: Lexical first, semantic as fallback (recommended)
- SEMANTIC_ONLY: SemanticConflictDetector only

#### Scenario: LEXICAL_ONLY uses negation detector only
- **WHEN** strategy is LEXICAL_ONLY and incoming "mars does not have water" is checked
- **THEN** only negation-based detection is used
- **AND** semantic detector is not invoked

#### Scenario: LEXICAL_THEN_SEMANTIC combines detectors
- **WHEN** strategy is LEXICAL_THEN_SEMANTIC
- **THEN** lexical detector runs first
- **AND** if no lexical conflict found, semantic detector runs on subject-filtered candidates

#### Scenario: SEMANTIC_ONLY uses LLM only
- **WHEN** strategy is SEMANTIC_ONLY
- **THEN** lexical detector is skipped
- **AND** all candidates are checked semantically (after subject filtering)

### Requirement: Composite conflict detector pipeline

The system SHALL provide a composite conflict detector that chains lexical and semantic detectors according to strategy. Both detector results SHALL be combined and returned together.

#### Scenario: Both lexical and semantic conflicts returned
- **WHEN** incoming text triggers both lexical negation and semantic opposition
- **THEN** composite detector returns all conflicts
- **AND** conflict list contains both types

#### Scenario: Lexical match stops further processing
- **WHEN** lexical detector finds conflict
- **THEN** semantic detector is not invoked (LEXICAL_THEN_SEMANTIC mode with match found)
- **AND** lexical conflict is returned immediately

### Requirement: Backward-compatible conflict detection API

The `ConflictDetector.detect()` method SHALL maintain its existing signature and contract. Callers observe no change in return type or behavior with LEXICAL_ONLY strategy.

#### Scenario: API signature unchanged
- **WHEN** calling `detect(incomingText, existingUnits)`
- **THEN** return type is `List<Conflict>`
- **AND** method accepts same parameters as before

#### Scenario: LEXICAL_ONLY behavior matches legacy
- **WHEN** strategy is LEXICAL_ONLY
- **THEN** conflict detection results match previous NegationConflictDetector behavior exactly

## Invariants

- **I1**: Subject filter MUST NOT mutate incoming text or memory unit texts
- **I2**: Semantic detector MUST NOT be invoked if subject filter returns empty (no shared subjects)
- **I3**: If lexical detector returns conflicts (LEXICAL_THEN_SEMANTIC), semantic detector MUST NOT be invoked
- **I4**: All conflicts returned MUST include reason text explaining the opposition
- **I5**: Strategy enum MUST match supported values (LEXICAL_ONLY, LEXICAL_THEN_SEMANTIC, SEMANTIC_ONLY)
