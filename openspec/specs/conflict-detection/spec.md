## ADDED Requirements

### Requirement: LlmConflictDetector

An `LlmConflictDetector` SHALL implement the `ConflictDetector` interface using LLM-based semantic analysis. For each new proposition, the detector SHALL compare it against all active anchors in the same contextId by sending the proposition text and anchor text to a configured LLM model. The LLM SHALL be instructed to determine whether the two statements are contradictory and return a structured yes/no response with an explanation. The detector SHALL use a cheap, fast model (configurable via `dice-anchors.conflict-detection.model`, default: gpt-4o-nano).

#### Scenario: Semantic contradiction detected
- **WHEN** a new proposition states "the bridge collapsed last winter" and an active anchor states "the bridge is intact"
- **THEN** the LlmConflictDetector returns a ConflictResult indicating contradiction with an explanation

#### Scenario: No contradiction between compatible statements
- **WHEN** a new proposition states "the king held a feast" and an active anchor states "the king is alive"
- **THEN** the LlmConflictDetector returns no conflict

#### Scenario: Vocabulary variation handled
- **WHEN** a new proposition states "the monarch perished" and an active anchor states "the king is alive"
- **THEN** the LlmConflictDetector detects the contradiction despite different vocabulary

### Requirement: ConflictDetector strategy selection

The application SHALL support selecting the conflict detection strategy via configuration property `dice-anchors.conflict-detection.strategy`. Valid values SHALL be `lexical` (existing NegationConflictDetector) and `llm` (new LlmConflictDetector). The default SHALL be `llm`. Both implementations SHALL implement the same `ConflictDetector` interface. The `AnchorEngine` SHALL use whichever implementation is configured.

#### Scenario: LLM strategy selected
- **WHEN** `dice-anchors.conflict-detection.strategy=llm` is configured
- **THEN** the `LlmConflictDetector` bean is active and used by AnchorEngine

#### Scenario: Lexical fallback
- **WHEN** `dice-anchors.conflict-detection.strategy=lexical` is configured
- **THEN** the `NegationConflictDetector` bean is active and used by AnchorEngine

### Requirement: Conflict detection prompt

The LLM conflict detection prompt SHALL focus strictly on factual contradiction. The prompt SHALL instruct the model: "Determine if Statement A and Statement B are factually contradictory. Two statements contradict if they cannot both be true simultaneously. Narrative progression is NOT contradiction (e.g., 'the king is alive' and 'the king was assassinated during the siege' are progression, not contradiction if the siege happened after the first statement)." The response SHALL be JSON with fields: `contradicts` (boolean) and `explanation` (String).

#### Scenario: Temporal progression not flagged
- **WHEN** anchor states "the village is peaceful" and proposition states "the village was attacked last night"
- **THEN** the detector does NOT flag this as contradiction (temporal progression)

#### Scenario: Simultaneous incompatibility flagged
- **WHEN** anchor states "the door is locked" and proposition states "the door stands wide open"
- **THEN** the detector flags this as contradiction (cannot both be true simultaneously)
