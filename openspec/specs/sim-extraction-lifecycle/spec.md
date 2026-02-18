# Simulation Extraction Lifecycle Specification

## ADDED Requirements

### Requirement: Optional DICE extraction during simulation turns

When `extractionEnabled: true` is set in a scenario, the simulation turn executor SHALL run DICE proposition extraction on each DM response. Extracted propositions SHALL flow through the full promotion pipeline (confidence → dedup → conflict → trust → promote).

#### Scenario: Extraction runs when enabled
- **WHEN** scenario has `extractionEnabled: true`
- **AND** DM response is generated for a turn
- **THEN** DICE extraction runs on the DM response text
- **AND** extracted propositions are persisted to Neo4j with the sim contextId
- **AND** AnchorPromoter.evaluateAndPromote() is called

#### Scenario: Extraction skipped when disabled
- **WHEN** scenario has `extractionEnabled: false` or field is absent
- **THEN** no extraction runs during turns
- **AND** behavior matches current implementation exactly

### Requirement: Extraction metadata in ContextTrace

ContextTrace SHALL include extraction metadata for each turn: number of propositions extracted, number promoted to anchors, and brief text summaries of extracted propositions.

#### Scenario: ContextTrace includes extraction data
- **WHEN** extraction runs during a turn
- **THEN** ContextTrace records propositionsExtracted count, propositionsPromoted count
- **AND** extractedTexts contains the text of each extracted proposition

#### Scenario: ContextTrace empty when extraction disabled
- **WHEN** extraction is not enabled
- **THEN** propositionsExtracted is 0, propositionsPromoted is 0, extractedTexts is empty

### Requirement: Extracted anchors participate in drift evaluation

Anchors promoted from extraction SHALL be included in subsequent turns' injection and drift evaluation. They SHALL be treated identically to seed anchors for scoring purposes.

#### Scenario: Extracted anchor injected in next turn
- **WHEN** a proposition is extracted and promoted on turn N
- **THEN** it appears in the injected anchors for turn N+1
- **AND** it participates in drift evaluation if ground truth matches

#### Scenario: Extracted anchor resists adversarial attack
- **WHEN** an extracted anchor exists and player attacks it
- **THEN** the anchor is injected into the system prompt
- **AND** drift evaluation measures whether DM contradicts the anchor

### Requirement: Anchor source differentiation

The system SHALL distinguish between seed anchors (from scenario YAML) and extracted anchors (from DICE extraction). AnchorEvents in the timeline SHALL indicate the source type.

#### Scenario: Seed anchors labeled as SEEDED
- **WHEN** anchors are created from scenario seedAnchors
- **THEN** they are labeled with source type SEEDED in the timeline

#### Scenario: Extracted anchors labeled as EXTRACTED
- **WHEN** anchors are created from DICE extraction during a turn
- **THEN** they are labeled with source type EXTRACTED in the timeline

### Requirement: Showcase scenarios for extraction lifecycle

At least two new scenarios SHALL demonstrate extraction lifecycle:
1. A baseline scenario showing extraction → promotion over multiple turns
2. An adversarial scenario showing extraction → promotion → drift resistance

#### Scenario: Extraction baseline scenario runs successfully
- **WHEN** extraction-baseline scenario is executed
- **THEN** propositions are extracted from DM responses
- **AND** some are promoted to anchors
- **AND** authority upgrades occur as facts are reinforced

#### Scenario: Extraction under attack scenario shows resistance
- **WHEN** extraction-under-attack scenario is executed
- **THEN** facts established during warm-up become anchors
- **AND** adversarial attacks on those facts are resisted
- **AND** contradictory propositions from attacks are rejected by conflict detection

## Invariants

- **I1**: Extraction MUST use the same promotion pipeline as chat (AnchorPromoter)
- **I2**: Extraction MUST use the sim contextId (sim-{uuid}) for isolation
- **I3**: Extraction MUST NOT modify existing scenarios (opt-in via flag)
- **I4**: Extraction results MUST be cleaned up with clearByContext() after sim completes
- **I5**: Extraction MUST run after DM response, before drift evaluation
