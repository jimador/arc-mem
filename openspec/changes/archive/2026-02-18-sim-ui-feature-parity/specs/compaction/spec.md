## ADDED Requirements

### Requirement: CompactedContextProvider

A `CompactedContextProvider` SHALL wrap the existing anchor context assembly with compaction awareness. The provider SHALL track message history size and evaluate compaction triggers: token threshold exceeded, message count threshold exceeded, or forced compaction at specific turns (as defined in scenario YAML). When triggered, the provider SHALL compact the conversation by generating a summary and removing compacted messages while preserving protected content. The provider SHALL prepend summaries from an in-memory summary store to the context.

#### Scenario: Token threshold triggers compaction
- **WHEN** the accumulated token count exceeds the configured tokenThreshold (e.g., 4000 tokens)
- **THEN** compaction is triggered and a summary is generated for the oldest messages

#### Scenario: Forced compaction at specified turn
- **WHEN** the scenario YAML specifies `forceAtTurns: [10, 20]` and turn 10 completes
- **THEN** compaction is triggered regardless of token count

#### Scenario: Summary prepended to context
- **WHEN** compaction has occurred and a subsequent turn assembles the context
- **THEN** the generated summary is prepended to the conversation history

### Requirement: AnchorContentProtector

An `AnchorContentProtector` SHALL implement the `ProtectedContentProvider` SPI to protect messages that back ACTIVE anchors from being compacted. Protection priority SHALL equal the anchor's rank (higher rank = higher protection priority). Messages associated with anchors ranked at or above the budget threshold SHALL always be protected. The protector SHALL query `AnchorRepository` to determine which messages are anchor-backing.

#### Scenario: High-rank anchor message protected
- **WHEN** compaction runs and a message backs an anchor with rank 800
- **THEN** the message is marked as protected with priority 800 and is not compacted

#### Scenario: Low-rank anchor message eligible for compaction
- **WHEN** compaction runs and the only anchor backing a message has rank 150
- **AND** the token budget requires compacting that message
- **THEN** the message MAY be compacted if its protection priority is below the compaction threshold

### Requirement: PropositionContentProtector

A `PropositionContentProtector` SHALL implement the `ProtectedContentProvider` SPI to protect messages backing unpromoted propositions (EXTRACTED or REINFORCED status). Protection priority SHALL be `trustScore * 100` (where trustScore is the proposition's trust score, defaulting to `confidence * 100` if trust scoring is not active). This protector has lower priority than `AnchorContentProtector`.

#### Scenario: High-trust proposition message protected
- **WHEN** compaction runs and a message backs a proposition with trustScore=0.85
- **THEN** the message has protection priority 85

#### Scenario: No trust score defaults to confidence
- **WHEN** trust scoring is not active and a proposition has confidence=0.72
- **THEN** the protection priority is 72

### Requirement: SimSummaryGenerator

A `SimSummaryGenerator` SHALL generate D&D-aware narrative summaries using the `ChatModel`. The generator SHALL accept a list of messages to summarize and produce a concise narrative summary that preserves key facts, character actions, and plot developments. The generated summary SHALL be stored in an in-memory summary store keyed by contextId and turn range.

#### Scenario: Summary preserves key facts
- **WHEN** messages from turns 1-10 are summarized and those turns established 3 ground truth facts
- **THEN** the generated summary contains references to all 3 established facts

#### Scenario: Summary stored for later retrieval
- **WHEN** a summary is generated for turns 1-10 of context "sim-abc123"
- **THEN** the summary is retrievable from the summary store by contextId and turn range

### Requirement: CompactionDriftEvaluator

A `CompactionDriftEvaluator` SHALL compare anchor and proposition state before and after a compaction cycle to detect `COMPACTION_LOSS` drift. The evaluator SHALL take before/after snapshots of protected content IDs. Any content that was protected before compaction but missing after SHALL be reported as a COMPACTION_LOSS event with the affected anchor/proposition IDs.

#### Scenario: No loss detected
- **WHEN** compaction runs and all protected content survives
- **THEN** the CompactionDriftEvaluator reports zero COMPACTION_LOSS events

#### Scenario: Loss detected
- **WHEN** compaction removes a message that was backing a protected proposition
- **THEN** the CompactionDriftEvaluator reports a COMPACTION_LOSS event identifying the affected proposition

### Requirement: Compaction tab in ContextInspectorPanel

The `ContextInspectorPanel` SHALL include a third tab labeled "Compaction" that displays compaction details for the selected turn. The tab SHALL show: trigger reason (token threshold / message threshold / forced), token savings (tokens before - tokens after), protected content list per provider (anchor protector, proposition protector), summary preview (first 200 characters of generated summary), and compaction duration. The tab SHALL show "No compaction" for turns where compaction did not occur.

#### Scenario: Compaction details displayed
- **WHEN** the user selects a turn where compaction occurred
- **THEN** the Compaction tab shows trigger reason, token savings, protected content, summary preview, and duration

#### Scenario: No compaction turn
- **WHEN** the user selects a turn where no compaction occurred
- **THEN** the Compaction tab shows "No compaction on this turn."

### Requirement: Compaction configuration in scenario YAML

`SimulationScenario` SHALL support a `compactionConfig` section with fields: `enabled` (boolean, default false), `forceAtTurns` (List<Integer>, turns at which compaction is forced), `tokenThreshold` (int, token count that triggers compaction), and `messageThreshold` (int, message count that triggers compaction). The `ScenarioLoader` SHALL parse this section. When `enabled` is false or the section is absent, no compaction occurs.

#### Scenario: Compaction enabled with thresholds
- **WHEN** a scenario YAML contains `compactionConfig: { enabled: true, tokenThreshold: 4000, messageThreshold: 20 }`
- **THEN** compaction triggers when either threshold is exceeded

#### Scenario: Compaction disabled by default
- **WHEN** a scenario YAML has no `compactionConfig` section
- **THEN** compaction does not occur during the simulation

### Requirement: CompactionIntegrityAssertion

A `CompactionIntegrityAssertion` SHALL verify that compaction did not result in loss of critical facts. The assertion SHALL check that all ground truth facts are still represented in the anchor set or summary after compaction. This assertion integrates with the assertion-framework capability and can be configured per-scenario in the assertions YAML section.

#### Scenario: Integrity assertion passes
- **WHEN** compaction occurs and all ground truth facts remain accessible via anchors or summary
- **THEN** the CompactionIntegrityAssertion passes

#### Scenario: Integrity assertion fails on fact loss
- **WHEN** compaction removes evidence for a ground truth fact and no anchor covers it
- **THEN** the CompactionIntegrityAssertion fails with details identifying the lost fact
